package com.autobizlogic.abl.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

import org.hibernate.SessionFactory;

import com.autobizlogic.abl.hibernate.HibernateConfiguration;
import com.autobizlogic.abl.util.LogicLogger;

/**
 * Handle all configuration parameters for ABL.
 */
public class LogicConfiguration {
	
	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.GENERAL);

	/**
	 * The valid property names for the configuration. See the config file for comments on each.
	 */
	public enum PropertyName {
		
		AGGREGRATE_DEFAULT_OVERRIDE("aggregateDefaultOverride", null),
		BUSINESS_LOGIC_FINDER("businessLogicFinder", "com.autobizlogic.abl.logic.SystemBusinessLogicFinder"),
		BUSINESS_LOGIC_FACTORY("businessLogicFactory", "com.autobizlogic.abl.logic.BusinessLogicFactoryImpl"),
		CONSOLE_SERVER_URL("consoleServerUrl", null),
		CURRENT_SESSION_CONTEXT_CLASS("currentSessionContextClass", null),
		
		/**
		 * The name of the project for database-loaded logic (available only in Pro edition)
		 */
		DATABASE_LOGIC_PROJECT("databaseLogicProject", null),
		DATABASE_LOGIC_REFRESH_INTERVAL("databaseLogicRefreshInterval", null),
		
		ENTITY_PROCESSOR("entityProcessor", null),
		GLOBAL_EVENT_LISTENERS("globalEventListeners", null),
		GLOBAL_TRANSACTION_SUMMARY_LISTENERS("globalTransactionSummaryListeners", null),
		INVOKE_FORMULA_METHODS("invokeFormulaMethods", "true"),
		LOGIC_CLASS_MANAGER("logicClassManager", null),
		LOGIC_CLASS_SUFFIX("logicClassSuffix", "Logic"),
		LOGIC_PACKAGE_NAMES("logicPackageNames", null),
		PARALLEL_PACKAGE_NAME("parallelPackageName", "businesslogic"),
		PERSISTENT_PACKAGE_NAMES("persistentPackageNames", null),
		SESSION_CONTEXT_CLASS("sessionContextClass", null),
		WORK_MANAGER_NAME("workManagerName", null);
				
		private String name;
		private String defaultValue;
		
		private PropertyName(String name, String defaultValue) {
			this.name = name;
			this.defaultValue = defaultValue;
		}
		
		public String getName() {
			return name;
		}
		
		public String getDefaultValue() {
			return defaultValue;
		}
	}
	
	/**
	 * The sole instance of this class.
	 */
	private static LogicConfiguration instance = new LogicConfiguration();
	
	/**
	 * This where we keep the actual properties.
	 */
	private Properties props;
	
	/**
	 * Keep track of which SessionFactory instances have been set up by us. We use a WeakHashMap so as not to prevent
	 * the GC'ing of the SessionFactory.
	 */
	private Map<SessionFactory, Boolean> initializedFactories = Collections.synchronizedMap(new WeakHashMap<SessionFactory, Boolean>());

	/**
	 * The constructor is protected because this is a singleton.
	 */
	private LogicConfiguration() {
		props = new Properties();
		InputStream inStr = getClass().getResourceAsStream("/ABLConfig.properties");
		if (inStr != null) {
			try {
				props.load(inStr);
				if (log.isInfoEnabled()) {
					URL url = getClass().getResource("/ABLConfig.properties");
					log.info("Loaded ABLConfig.properties from " + url + ", values: " + props);
				}
			} catch (IOException ex) {
				throw new RuntimeException("Error while attempting to read ABL configuration file", ex);
			}
		}
		else {
			if (log.isWarnEnabled())
				log.warn("Could not find file ABLConfig.properties. Default configuration values will be used.");
		}
	}
	
	/**
	 * The only way to get the sole instance of this class.
	 * @return The sole instance of this class.
	 */
	public static LogicConfiguration getInstance() {
		return instance;
	}
	
	/**
	 * Get the value for a given property
	 * @param propName The name of the property
	 * @return The value of the property, or null if the property is not defined
	 */
	public String getProperty(PropertyName propName) {
		String prop = props.getProperty(propName.getName());
		if (prop == null || prop.trim().length() == 0)
			return propName.getDefaultValue();
		String propVal = props.getProperty(propName.getName());
		if (propVal != null)
			propVal = propVal.trim();
		return propVal;
	}
	
	/**
	 * Get the value for a property with a composed named such as name1.
	 * @param name The name of the property
	 * @return The value of the property, or null if it does not have a value
	 */
	public String getProperty(String name) {
		return props.getProperty(name);
	}
	
	/**
	 * Get all the properties whose name starts with the given prefix.
	 * If a property is name1_foo = baz, then the result for "name1" will contain
	 * a key "foo" with value "baz".
	 * @param prefix The start of the property names
	 * @return A Map containing all the found properties. Empty if none found.
	 */
	public Map<String, String> getPropertiesStartingWith(String prefix) {
		Map<String, String> result = new HashMap<String, String>();
		for (Object key : props.keySet()) {
			String keyStr = (String)key;
			if (keyStr.startsWith(prefix)) {
				result.put(keyStr.substring(prefix.length()), (String)props.get(key));
			}
		}
		return result;
	}
	
	/**
	 * Determine whether the given property has a value
	 * @param propName The name of the property
	 * @return True if the property has a non-empty value, false otherwise
	 */
	public boolean hasProperty(PropertyName propName) {
		String prop = this.getProperty(propName);
		return prop != null && prop.trim().length() > 0;
	}
	
	/**
	 * Set the value of a property.
	 * @param propName The name of the property
	 * @param value The value to which to set it. If null, the property is set to its default value.
	 */
	public void setProperty(PropertyName propName, String value) {
		if (value == null)
			props.setProperty(propName.getName(), propName.getDefaultValue());
		else
			props.setProperty(propName.getName(), value);
	}
	
	/**
	 * Set the value of a property. This is meant to be used for those properties that have a
	 * composite name.
	 * @param propName The name of the property
	 * @param value The value of the property
	 */
	public void setRawProperty(String propName, String value) {
		props.setProperty(propName, value);
	}
	
	/**
	 * Register a Hibernate session factory with ABL. This can be called more than once
	 * for a given SessionFactory.
	 */
	public void registerSessionFactory(SessionFactory factory) {
		if (initializedFactories.containsKey(factory))
			return;
		
		HibernateConfiguration.registerSessionFactory(factory);
		initializedFactories.put(factory, Boolean.TRUE);
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicConfiguration.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 