package com.autobizlogic.abl.logic;

import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.util.ClassNameUtil;
import com.autobizlogic.abl.util.LogicLogger;

/**
 * The system class for finding logic classes. This can be overridden in the configuration.
 */
public class SystemBusinessLogicFinder implements BusinessLogicFinder{
	
	private static BusinessLogicFinder instance = null;
	
	/**
	 * Cache for mapping bean classes to their logic class (if any).
	 * If the bean class has already been seen, and no logic class exists for it, the value
	 * will be an empty string.
	 */
	private Map<String, String> logicClassNames = new HashMap<String, String>();
	
	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.DEPENDENCY);

	/**
	 * Get the sole instance of this class, or of some other implementation of BusinessLogicFinder
	 * as defined in the config file.
	 */
	public static BusinessLogicFinder getInstance() {
		synchronized(SystemBusinessLogicFinder.class) {
			if (instance == null) {
				String finderClassName = 
						LogicConfiguration.getInstance().getProperty(PropertyName.BUSINESS_LOGIC_FINDER);
				try {
					instance = (BusinessLogicFinder)Class.forName(finderClassName).newInstance();
				}
				catch(Exception ex) {
					throw new RuntimeException("Unable to load business logic finder class: " + finderClassName);
				}
			}
		}
		return instance;
	}
	
	/**
	 * Reset the instance of this class. Use this only if you have changed 
	 * the PropertyName.BUSINESS_LOGIC_FINDER value in the configuration and 
	 * you need the instance to be recreated from scratch.
	 */
	public static void resetInstance() {
		instance = null;
	}

	/**
	 * Given the name of an entity, get the name of the corresponding business logic class.
	 * @param clsFullName The full name of the bean class
	 * @return The full name of the business logic class, or null if no business logic class could be found.
	 */
	@Override
	public Class<?> getLogicClassForBeanName(String clsFullName) {
		
		// Have we seen this class before?
		String cachedLogicName = logicClassNames.get(clsFullName);
		if (cachedLogicName != null && cachedLogicName.length() > 0)
			return loadClass(cachedLogicName);
		if (cachedLogicName != null && cachedLogicName.length() == 0) // We've already looked for it, and it's not there
			return null;
		
		String clsName = clsFullName;
		String clsPackageName = "";
		int lastDotIdx = clsFullName.lastIndexOf('.');
		if (lastDotIdx >= 0) { // In case the class doesn't have a package. Stranger things have happened.
			clsName = clsFullName.substring(lastDotIdx + 1);
			clsPackageName = clsFullName.substring(0, lastDotIdx);
		}
		String logicClassName = null;
		
		// We now know the bean's package and the name of the bean class. First of all, has the user specified
		// where the logic should be found?
		String possiblePackages = LogicConfiguration.getInstance().getProperty(LogicConfiguration.PropertyName.LOGIC_PACKAGE_NAMES);
		
		String parallelPackageName = LogicConfiguration.getInstance().getProperty(PropertyName.PARALLEL_PACKAGE_NAME);
		String logicClassSuffix = LogicConfiguration.getInstance().getProperty(PropertyName.LOGIC_CLASS_SUFFIX);
		
		// If the user has told us where to find the logic classes, look there
		if (possiblePackages != null && possiblePackages.trim().length() > 0) {
			String[] packages = possiblePackages.split(",");
			for (String pkg : packages) {
				String possibleLogicName = pkg.trim() + "." + clsName + logicClassSuffix;
				if (classExists(possibleLogicName)) {
					logicClassName = possibleLogicName;
					break;
				}
			}
		}
		// No package specified in the configuration file -- assume a parallel package
		else {
			if (log.isDebugEnabled())
				log.debug("Looking for logic class in default package for " + clsFullName);
			String possibleLogicName = null;
			int lastIdx = clsPackageName.lastIndexOf('.');
			if (lastIdx > 0) { // Package has at least two levels (e.g. a.b)
				String logicPkgName = clsPackageName.substring(0, lastIdx) + "." + parallelPackageName;
				possibleLogicName = logicPkgName + "." + clsName + logicClassSuffix;
			}
			else if (clsPackageName.length() > 0) { // Package has only one level
				possibleLogicName = parallelPackageName + "." + clsName + logicClassSuffix;
			}
			else { // No package
				possibleLogicName = clsName + logicClassSuffix;
			}

			if (classExists(possibleLogicName)) {
				logicClassName = possibleLogicName;
			}
		}
		
		// No point in doing this again for the same bean class -- cache the result
		if (logicClassName == null) {
			logicClassNames.put(clsFullName, ""); // Empty string means we've looked for it and it ain't there
			return null;
		}

		logicClassNames.put(clsFullName, logicClassName);
		return loadClass(logicClassName);
	}
	
	/**
	 * Get the full name of the logic class for a bean of the given class name
	 * @param obj The bean in question
	 * @return The full name of the logic class, or null if there is none
	 */
	protected static Class<?> getLogicClassForBean(Object obj) {
		String clsName = ClassNameUtil.getEntityNameForBean(obj);
		return getInstance().getLogicClassForBeanName(clsName);
	}
	
	protected static Class<?> getLogicClassForEntity(MetaEntity entity) {
		String entityName = entity.getEntityName();
		if (entity.isPojo())
			return getInstance().getLogicClassForBeanName(entityName);
		
		return getInstance().getLogicClassForEntityName(entityName);
	}
	
	/**
	 * Get the name of the class that (if it exists) would contain the business logic for the given
	 * map (i.e. non-POJO) entity. This currently looks only in the "businesslogic" package,
	 * but we'll improve that soon.
	 */
	@Override
	public Class<?> getLogicClassForEntityName(String entityName) {
		// Have we seen this entity before?
		String cachedLogicName = logicClassNames.get(entityName);
		if (cachedLogicName != null && cachedLogicName.length() > 0)
			return loadClass(cachedLogicName);
		if (cachedLogicName != null && cachedLogicName.length() == 0) // We've already looked for it, and it's not there
			return null;
		
		// We have not yet seen this entity -- see if we can find a logic class for it.
		String possiblePackages = LogicConfiguration.getInstance().getProperty(LogicConfiguration.PropertyName.LOGIC_PACKAGE_NAMES);
		String logicClassSuffix = LogicConfiguration.getInstance().getProperty(PropertyName.LOGIC_CLASS_SUFFIX);
		String logicClassName = null;
		
		// If the user has told us where to find the logic classes, look there
		if (possiblePackages != null && possiblePackages.trim().length() > 0) {
			String[] packages = possiblePackages.split(",");
			for (String pkg : packages) {
				String possibleLogicName = pkg.trim() + "." + entityName + logicClassSuffix;
				if (classExists(possibleLogicName)) {
					logicClassName = possibleLogicName;
					break;
				}
			}
		}
		else {
			String possibleLogicName = "businesslogic." + entityName + "Logic";			
			if (classExists(possibleLogicName))
				logicClassName = possibleLogicName;
		}
		
		// No point in doing this again for the same bean class -- cache the result
		if (logicClassName == null) {
			logicClassNames.put(entityName, ""); // Empty string means we've looked for it and it ain't there
			return null;
		}

		logicClassNames.put(entityName, logicClassName);
		return loadClass(logicClassName);
	}
	
	/**
	 * Clear the cache. This must be called if the path for finding logic classes is changed
	 * at runtime.
	 */
	public void resetCache() {
		logicClassNames = new HashMap<String, String>();
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * See if a class with the given name can be found in the set of currently known
	 * class loaders. If not, return false.
	 */
	private static boolean classExists(String className) {
		Class<?> theLogicClass = null;
		try {
			theLogicClass = ClassLoaderManager.getInstance().getLogicClassFromName(className);
		}
		catch (Exception ex) {
			// Do nothing
			if (log.isDebugEnabled()) {
				log.debug("SystemBusinessLogicFinder.classExists : candidate logic class " + 
						className + " was not found - ignoring");
				if (ex.getCause() == null || ! (ex.getCause() instanceof ClassNotFoundException)) {
					log.debug("Error while loading logic class " + className, ex);
				}
			}
		}
		return theLogicClass != null;
	}
	
	/**
	 * Load the class with the given name. If the class does not exist, an exception is thrown.
	 */
	private static Class<?> loadClass(String className) {
		Class<?> theLogicClass = null;
		try {
			theLogicClass = ClassLoaderManager.getInstance().getLogicClassFromName(className);
		}
		catch (Exception ex) {
			throw new RuntimeException("Unable to load logic class: " + className, ex);
		}
		return theLogicClass;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  SystemBusinessLogicFinder.java 724 2012-02-08 20:20:31Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 