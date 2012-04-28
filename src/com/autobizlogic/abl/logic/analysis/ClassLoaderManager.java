package com.autobizlogic.abl.logic.analysis;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.WeakHashMap;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;
import com.autobizlogic.abl.logic.SystemBusinessLogicFinder;
import com.autobizlogic.abl.logic.dynamic.LogicClassManager;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.LogicLogger;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

/**
 * Handle all class loader dependencies. This singleton needs to be notified of class loaders
 * that are being encountered. All classes should be retrieved from it.
 */
public class ClassLoaderManager {
	
	private static ClassLoaderManager instance = new ClassLoaderManager();

	/**
	 * The shared instance of ClassPool
	 */
	private ClassPool classPool;
	
	/**
	 * All the class loaders we know about.
	 */
	private Map<ClassLoader, Boolean> classLoaders = Collections.synchronizedMap(new WeakHashMap<ClassLoader, Boolean>());
	
	/**
	 * The current logic class managers
	 */
	private List<LogicClassManager> logicClassManagers;
	
	/**
	 * When was the last time we checked if the classes needed reloading?
	 */
	private long lastRefreshCheck = 0;

	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.DEPENDENCY);

	private ClassLoaderManager() {
		classPool = new ClassPool();
		ClassPath cpath = new LoaderClassPath(getClass().getClassLoader());
		classPool.appendClassPath(cpath);
	}
	
	/**
	 * Retrieve the sole instance of this class.
	 */
	public static ClassLoaderManager getInstance() {
		return instance;
	}

	/**
	 * Notify the ClassLoaderManager that the given bean exists, and that its ClassLoader should be
	 * remembered.
	 * @param bean A persistent bean
	 */
	public void addClassLoaderFromBean(Object bean) {
		ClassLoader cl = bean.getClass().getClassLoader();
		if (classLoaders.containsKey(cl))
			return;
		
		addClassLoader(cl);
	}
	
	/**
	 * Notify the ClassLoaderManager that the given ClassLoader should be remembered.
	 */
	public void addClassLoader(ClassLoader clsLoader) {
		synchronized(classLoaders) {
			if ( ! classLoaders.containsKey(clsLoader)) {
				LoaderClassPath lcp = new LoaderClassPath(clsLoader);
				classPool.appendClassPath(lcp);
				classLoaders.put(clsLoader, Boolean.TRUE);
			}
		}
	}
	
	/**
	 * Get the class for a logic class. This is used only for logic classes, and allows
	 * dynamic reloading of classes.
	 * @return Null if the logic class cannot be found.
	 */
	public Class<?> getLogicClassFromName(String name) {
		initializeLogicClassManagers();
		if (logicClassManagers.size() == 0) {
			return getClassFromName(name);
		}
		
		Class<?> logicClass = null;
		for (LogicClassManager lcm : logicClassManagers) {
			logicClass = lcm.getClassForName(name);
			if (logicClass != null)
				return logicClass;
		}
		
		return getClassFromName(name);
	}
	
	/**
	 * Get a class from its name. This will try all the class loaders until one succeeds.
	 * @param name The full class name, e.g. com.foo.bar.Customer
	 * @return The class if found, otherwise an exception will be thrown.
	 */
	public Class<?> getClassFromName(String name) {
		Class<?> cls = null;
		
		// First try the default class loading mechanism
		try {
			cls = Class.forName(name);
		}
		catch(Exception ex) {
			
			if (log.isDebugEnabled()) {
				if ( ! (ex instanceof ClassNotFoundException)) {
					log.debug("Error while trying to load logic class " + name, ex);
				}
			}

			// That didn't work -- now try all the class loaders we know about
			Set<ClassLoader> loaders = classLoaders.keySet();
			for (ClassLoader loader: loaders) {
				try {
					cls = loader.loadClass(name);
				}
				catch(Exception ex2) {
					if (log.isDebugEnabled()) {
						if ( ! (ex2 instanceof ClassNotFoundException)) {
							log.debug("Error while trying to load logic class " + name, ex2);
						}
					}
				}
				if (cls != null)
					break;
			}
		}
		
		if (cls == null) {
			if (log.isDebugEnabled()) {
				log.debug("Unable to load class for name: " + name);
			}
		}
		
		return cls;
	}
	
	/**
	 * Get a ClassLoader that uses this class to load classes.
	 */
	public ClassLoader getAllClassLoader() {
		return new ClassLoader(){
			
			@Override
			public Class<?> loadClass(String clsName) {
				return getClassFromName(clsName);
			}
		};
	}
	
	/**
	 * This should get called before accessing any logic class. It will check whether the given
	 * class has changed, and if so, it will reset a bunch of caches to trigger reloading.
	 * @return True if at least one logic class has changed, in which case all logic classes
	 * will be forgotten (to be reloaded on demand).
	 */
	public boolean checkForClassUpdate() {
		
		if (logicClassManagers == null)
			return false;
		
		if (System.currentTimeMillis() - lastRefreshCheck < 1000)
			return false;
		lastRefreshCheck = System.currentTimeMillis();
		
		boolean reloadNeeded = false;
		for (LogicClassManager lcm : logicClassManagers) {
			if (lcm.classesNeedsReloading()) {
				reloadNeeded = true;
			}
		}
		
		if ( ! reloadNeeded)
			return false;

		for (LogicClassManager lcm : logicClassManagers) {
			lcm.forgetAllClasses();
		}

		LogicAnalysisManager.reset();
		RuleManager.reset();
		SystemBusinessLogicFinder.resetInstance();
		BeanUtil.resetCaches();
		
		ctClassesCreated = new HashSet<String>();
		setupClassPool();
		
		return true;
	}
	
	/**
	 * Manually reset the logic classes. All logic classes will be forgotten, and reloaded
	 * and re-analyzed as needed.
	 */
	public synchronized void forgetAllLogicClasses() {
		
		LogicAnalysisManager.reset();
		RuleManager.reset();
		SystemBusinessLogicFinder.resetInstance();
		BeanUtil.resetCaches();

		ctClassesCreated = new HashSet<String>();
		setupClassPool();
		
		if (logicClassManagers != null) {
			for (LogicClassManager logMgr : logicClassManagers) {
				logMgr.forgetAllClasses();
			}
		}
	}
	
	/////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Internal method. Set up the ClassPool.
	 */
	private void setupClassPool() {
		classPool = new ClassPool();

		for (ClassLoader cl : classLoaders.keySet()) {
			if (cl == null)
				continue;
			LoaderClassPath lcp = new LoaderClassPath(cl);
			classPool.appendClassPath(lcp);
		}

		ClassPath cpath = new LoaderClassPath(getClass().getClassLoader());
		classPool.appendClassPath(cpath);
	}
	
	/**
	 * Internal method. Get the CtClass for this class.
	 * @return The CtClass for this class.
	 */
	public CtClass getClassInfo(String className)
	{
		preloadClassIntoClassPool(className);
		CtClass ctClass = null;
		try {
			ctClass = classPool.get(className);
		}
		catch(NotFoundException ex)
		{
			throw new DependencyException("Unable to find class : " + className);
		}
		return ctClass;
	}
	
	/**
	 * Manually define a class. This is used for unusual cases when classloaders do not
	 * behave as usual, for instance if there is some sort of runtime bytecode
	 * enhancement of the logic classes.
	 * @param clsName The name of the class, e.g. com.foo.MyClass
	 * @param bytecode The bytecode for the class
	 */
	public void defineClass(String clsName, byte[] bytecode) {
		if (ctClassesCreated.contains(clsName))
			return;
		if (bytecode == null)
			throw new RuntimeException("Cannot define class from null bytecode for class: " + clsName);
		ctClassesCreated.add(clsName);
		ByteArrayInputStream bais = new ByteArrayInputStream(bytecode);
		try {
			classPool.makeClass(bais);
		}
		catch(Exception ex) {
			throw new RuntimeException("Exception while trying to read class bytes from database for " + clsName, ex);
		}
	}
	
	/**
	 * Internal (and ugly) method: preload a class into the ClassPool.
	 * This may be necessary because normally, ClassPool ends up calling getResource to get the URL
	 * of the class file, which obviously does not work for some class loaders, such as the database one.
	 */
	private void preloadClassIntoClassPool(String clsName) {
		if (logicClassManagers == null || logicClassManagers.size() == 0)
			return;
		if (ctClassesCreated.contains(clsName))
			return;
		for (LogicClassManager lcm : logicClassManagers) {
			if (lcm.getClassForName(clsName) != null) {
				byte[] clsBytes = lcm.getByteCodeForClass(clsName);
				if (clsBytes != null)
					defineClass(clsName, clsBytes);
				return;
			}
		}
		throw new RuntimeException("Unable to preload logic class: " + clsName + ". Class not found.");
	}

	private Set<String> ctClassesCreated = new HashSet<String>();
	
	/**
	 * Internal method: create the logic class managers from the configuration.
	 */
	private void initializeLogicClassManagers() {
		if(logicClassManagers != null)
			return;
		logicClassManagers = new Vector<LogicClassManager>();
		
		for (int i = 1; i < 1000; i++) {
			String numSuffix = "" + i;
			String classManagerName = LogicConfiguration.getInstance().getProperty(
					PropertyName.LOGIC_CLASS_MANAGER.getName() + numSuffix);
			if (classManagerName == null || classManagerName.trim().length() == 0)
				break;
			
			if (classManagerName.equals("file"))
				classManagerName = "com.autobizlogic.abl.logic.dynamic.TimeStampClassManager";
			else if (classManagerName.equals("jar"))
				classManagerName = "com.autobizlogic.abl.logic.dynamic.JarClassManager";
			else if (classManagerName.equals("database"))
				classManagerName = "com.autobizlogic.abl.logic.dynamic.DatabaseClassManager";
			
			Map<String, String> params = LogicConfiguration.getInstance().getPropertiesStartingWith(
					PropertyName.LOGIC_CLASS_MANAGER.getName() + numSuffix + "_");
			LogicClassManager clsManager = null;
			try {
				Class<?> cls = ClassLoaderManager.getInstance().getClassFromName(classManagerName);
				Constructor<?> constructor = cls.getConstructor(Map.class);
				clsManager = (LogicClassManager)constructor.newInstance(params);
			}
			catch(Exception ex) {
				String msg = "Error instantiating logic class manager " + classManagerName;
				if ( ! params.isEmpty())
					msg += " with parameters " + params;
				throw new RuntimeException(msg, ex);
			}
			logicClassManagers.add(clsManager);
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ClassLoaderManager.java 864 2012-02-28 23:51:08Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 