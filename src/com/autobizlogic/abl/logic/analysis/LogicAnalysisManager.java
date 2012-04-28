package com.autobizlogic.abl.logic.analysis;

import java.util.*;

import com.autobizlogic.abl.logic.SystemBusinessLogicFinder;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.util.ClassNameUtil;
import com.autobizlogic.abl.util.LogicLogger;

/**
 * The class responsible for creating and keeping track of logic class analyses.
 */
public class LogicAnalysisManager
{
	/**
	 * All the known instances, normally one per metamodel.
	 */
	private static Map<MetaModel, LogicAnalysisManager> instances = 
			Collections.synchronizedMap(new WeakHashMap<MetaModel, LogicAnalysisManager>());
	
	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.DEPENDENCY);
	
	/**
	 * The metamodel for this instance.
	 */
	private MetaModel metaModel;
	
	/**
	 * Cache for class analyses. Key is the entity name.
	 */
	private Map<String, LogicClassAnalysis> classAnalyses = new HashMap<String, LogicClassAnalysis>();

	/**
	 * Cache for class dependencies
	 */
	private Map<String, ClassDependency> dependencies = new HashMap<String, ClassDependency>();
	
	/**
	 * Private constructor since users should call getInstance
	 */
	private LogicAnalysisManager(MetaModel metaModel) {
		this.metaModel = metaModel;
	}
	
	/**
	 * Get the sole instance of this class.
	 * @return The sole instance of this class
	 */
	public static LogicAnalysisManager getInstance(MetaModel metaModel) {
		LogicAnalysisManager instance = instances.get(metaModel);
		if (instance == null) {
			instance = new LogicAnalysisManager(metaModel);
			instances.put(metaModel, instance);
			log.debug("Creating new LogicAnalysisManager for MetaModel " + metaModel);
		}
		return instance;
	}
	
	/**
	 * Get the logic analysis for a given class.
	 * @param metaEntity The meta entity in question
	 * @return The logic analysis if found, otherwise null
	 */
	public LogicClassAnalysis getLogicAnalysisForEntity(MetaEntity metaEntity) {
		
		String entityName = metaEntity.getEntityName();
		Class<?> logicClass;
		if (metaEntity.isPojo())
			logicClass = SystemBusinessLogicFinder.getInstance().getLogicClassForBeanName(entityName);
		else
			logicClass = SystemBusinessLogicFinder.getInstance().getLogicClassForEntityName(entityName);
		if (logicClass == null)
			return null;
		
		String logicClassName = logicClass.getName();
		return getAnalysisForLogicClassName(logicClassName, entityName);
	}
	
	/**
	 * Get the logic analysis for a given bean class.
	 * @param object An instance of the bean
	 * @return The logic analysis if found, otherwise null
	 */
	public LogicClassAnalysis getLogicAnalysisForBean(Object object) {
		String clsName = ClassNameUtil.getEntityNameForBean(object);
		return getLogicAnalysisForEntityName(clsName);
	}
	
	/**
	 * Given the full class name of a logic class, return the logic analysis for it.
	 * @param clsName the full class name of a logic class
	 * @return The logic analysis for the given class, or null if there is none
	 */
	public LogicClassAnalysis getLogicAnalysisForLogicClassName(String clsName) {
		return classAnalyses.get(clsName);
	}
	
	public LogicClassAnalysis getLogicAnalysisForEntityName(String entityName) {
		
		MetaEntity metaEntity = metaModel.getMetaEntity(entityName);
		return getLogicAnalysisForEntity(metaEntity);
	}	
	

	/**
	 * Get the object representing a dependency on a specific class.
	 * @param className The fully qualified name of the class, e.g. com.foo.MyClass
	 * @return The ClassDependency for the desired class
	 */
	public ClassDependency getDependencyForClass(String className) {
		
		synchronized(dependencies)
		{
			if ( ! dependencies.containsKey(className))
			{
				ClassDependency classDep = new ClassDependency(className);
				dependencies.put(className, classDep);
			}
			
			return dependencies.get(className);
		}
	}
	
	/**
	 * Reset all analyses to null. This is only used for testing, although it may get used
	 * for dynamic logic later on.
	 */
	public static void reset() {
		for (LogicAnalysisManager lam : instances.values()) {
			lam.classAnalyses = new HashMap<String, LogicClassAnalysis>();
			lam.dependencies = new HashMap<String, ClassDependency>();
		}
	}

	/////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Actually retrieve, or create if necessary, the LogicClassAnalysis.
	 * @param logicClassName The full name of the logic class, e.g. com.foo.businesslogic.CustomerLogic
	 * @param entityName The name of the persistent entity
	 * @return The LogicClassAnalysis in question.
	 */
	private LogicClassAnalysis getAnalysisForLogicClassName(String logicClassName, String entityName) {
		if ( ! classAnalyses.containsKey(logicClassName))
			synchronized(classAnalyses)
			{
				if ( ! classAnalyses.containsKey(logicClassName))
				{
					MetaEntity me = metaModel.getMetaEntity(entityName);
					LogicClassAnalysis classDep = new LogicClassAnalysis(logicClassName, me);
					classAnalyses.put(logicClassName, classDep);
				}
				
			}
		return classAnalyses.get(logicClassName);
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicAnalysisManager.java 432 2012-01-13 08:37:28Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 