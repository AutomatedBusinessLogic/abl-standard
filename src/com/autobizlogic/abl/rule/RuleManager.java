package com.autobizlogic.abl.rule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.autobizlogic.abl.logic.analysis.LogicAnalysisManager;
import com.autobizlogic.abl.logic.analysis.LogicClassAnalysis;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.ClassNameUtil;
import com.autobizlogic.abl.util.LogicLogger;

/**
 * The class that handles rules. You use it by retrieving an instance with getInstance,
 * then you can retrieve logic groups by various means.
 */

public class RuleManager {
	
	/**
	 * All the known instances, normally one per metamodel.
	 */
	private static Map<MetaModel, RuleManager> instances = 
			Collections.synchronizedMap(new WeakHashMap<MetaModel, RuleManager>());
	
	/**
	 * The metamodel for this RuleManager
	 */
	private MetaModel metaModel;

	/**
	 * Cache for logic groups (e.g. logic classes)
	 */
	private Map<String, LogicGroup> logicGroups = new HashMap<String, LogicGroup>();
	
	/**
	 * Keep track of all derivations. The key is a composite of the bean's name and the attribute's name,
	 * and the value is the rule that derives the attribute's value.
	 */
	private Map<String, AbstractRule> derivationRules = new ConcurrentHashMap<String, AbstractRule>();

	/**
	 * Set of all entity names which are relevant to business logic. This includes all entities with
	 * business logic (obviously), but also all entities on which business logic depends. For instance,
	 * in a sum, if the child entity has no business logic, any changes to a child object must still trigger
	 * the sum.
	 */
	private Map<MetaEntity, Boolean> relevantEntities = new HashMap<MetaEntity, Boolean>();
	
	protected static final LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.RULES_ENGINE);
	
	
	/**
	 * The constructor is private because the sole instance of this class should be retrieved
	 * using getInstance().
	 */
	private RuleManager(MetaModel metaModel) {
		this.metaModel = metaModel;
	}
	
	/**
	 * Retrieves the sole instance of this class
	 * @param metaModel The MetaModel for this RuleManager
	 * @return The instance for the given metamodel
	 */
	public static RuleManager getInstance(MetaModel metaModel) {
		synchronized(instances) {
			RuleManager instance = instances.get(metaModel);
			if (instance == null) {
				if (instances.get(metaModel) == null) {
					instance = new RuleManager(metaModel);
					instances.put(metaModel,  instance);
				}
			}
			return instance;
		}
	}
	
	/**
	 * Get the LogicGroup containing the business logic for a given bean
	 * @param bean An instance of the bean
	 * @return LogicGroup containing the business logic for a given bean
	 */
	public LogicGroup getLogicGroupForBean(Object bean) {
		return getLogicGroupForClassName(bean.getClass().getName());		
	}
	
	/**
	 * Get the LogicGroup containing the business logic for a given bean
	 * @param aClass 
	 * @return LogicGroup containing the business logic for a given bean class
	 */	
	public LogicGroup getLogicGroupForClass(Class<?> aClass) {
		String beanClassName = aClass.getName();
		return getLogicGroupForClassName(beanClassName);
	}
	
	/**
	 * Get the LogicGroup containing the business logic for a given bean
	 * @param aBeanClassName The full name of the bean class
	 * @return The LogicGroup for that bean, or null if there is none.
	 */
	public LogicGroup getLogicGroupForClassName(String rawBeanClassName) {
		
		String beanClassName = ClassNameUtil.getEntityNameForClassName(rawBeanClassName);
		return getLogicGroupForEntityName(beanClassName);
	}
	
	public LogicGroup getLogicGroupForEntityName(String entityName) {
		
		LogicGroup group = logicGroups.get(entityName);

		if (group == null) {
			synchronized(logicGroups) {
				if (logicGroups.get(entityName) == null) {
					if (logicGroups.containsKey(entityName)) // We've already determined that there is no business logic for this bean
						return null;
					LogicClassAnalysis classAnalysis = LogicAnalysisManager.getInstance(metaModel)
							.getLogicAnalysisForEntityName(entityName);
					if (classAnalysis == null) {
						logicGroups.put(entityName, null);
						return null;
					}
					group = new LogicGroup(classAnalysis.getLogicClassName(), classAnalysis.getMetaEntity());
					logicGroups.put(entityName, group);
				}
			}
		}
		return group;
	}

	public LogicGroup getLogicGroupForEntity(MetaEntity entity) {
		return getLogicGroupForEntityName(entity.getEntityName());
	}
	
	/**
	 * Determine whether an instance of the given entity should trigger business logic.
	 */ 
	public boolean entityIsRelevant(MetaEntity metaEntity) {
		
		// Have we already determined whether this entity is relevant?
		Boolean isRelevant = relevantEntities.get(metaEntity);
		if (isRelevant != null)
			return isRelevant;
		
		// If the entity has a LogicGroup of its own, clearly it's relevant
		
		LogicGroup logicGroup = RuleManager.getInstance(metaEntity.getMetaModel()).getLogicGroupForEntity(metaEntity);
		if (logicGroup != null) {
			relevantEntities.put(metaEntity, true);
			return true;
		}
		
		RuleManager ruleManager = RuleManager.getInstance(metaEntity.getMetaModel());

		// Now look at the parent entities
		Set<MetaRole> rolesToParents = metaEntity.getRolesFromChildToParents();
		for (MetaRole roleToParent : rolesToParents) {
			MetaEntity parentEntity = roleToParent.getOtherMetaEntity();
			LogicGroup parentLg = ruleManager.getLogicGroupForEntity(parentEntity);
			if (parentLg == null)
				continue;
			
			for (AbstractRule rule : parentLg.getAggregates())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}
		}
		
		// Next look at the child entities
		Set<MetaRole> rolesToChildren = metaEntity.getRolesFromParentToChildren();
		for (MetaRole roleToChild : rolesToChildren) {
			MetaEntity childEntity = roleToChild.getOtherMetaEntity();
			LogicGroup childLg = ruleManager.getLogicGroupForEntity(childEntity);
			if (childLg == null)
				continue;
			
			for (AbstractRule rule : childLg.getActions())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}

			for (AbstractRule rule : childLg.getCommitActions())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}

			for (AbstractRule rule : childLg.getCommitConstraints())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}

			for (AbstractRule rule : childLg.getConstraints())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}

			for (AbstractRule rule : childLg.getEarlyActions())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}

			for (AbstractRule rule : childLg.getFormulas())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}

			for (AbstractRule rule : childLg.getParentCopies())
				for (RuleDependency dep : rule.getDependencies())
					if (dep.getBeanClassName().equals(metaEntity.getEntityName())) {
						relevantEntities.put(metaEntity, true);
						return true;
					}
		}

		// We have not found anything that uses this class, so it's not relevant
		relevantEntities.put(metaEntity, false);
		return false;
	}
	
	/**
	 * Tell the RuleManager that the given entity is relevant to business logic. This avoids
	 * having to dig through it if we already know that it is relevant.
	 */
	public void addRelevantEntity(MetaEntity metaEntity) {
		relevantEntities.put(metaEntity, true);
	}
	
	/**
	 * This should get called if the given logic class has changed and needs to be reanalyzed
	 * @param clsName
	 */
	public static void reset() {
		for (RuleManager rm : instances.values()) {
			if (rm == null)
				continue;
			rm.logicGroups = new HashMap<String, LogicGroup>();
			rm.derivationRules = new ConcurrentHashMap<String, AbstractRule>();
			rm.relevantEntities = new HashMap<MetaEntity, Boolean>();
		}
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Given the name of a bean and of one of its attributes, retrieve the rule that derives its value (if any)
	 * @param beanClassName The full name of the class of the persistent bean
	 * @param attributeName The name of the bean's attribute
	 * @return The rule that derives the attribute's value, or null if none.
	 */
	protected AbstractRule getDerivationRuleForAttribute(String beanClassName, String attributeName) {
		String fullName = beanClassName + "/" + attributeName;
		return derivationRules.get(fullName);
	}
	
	/**
	 * Add a rule to the list of derived rules.
	 * @param beanClassName The bean class name
	 * @param attributeName The bean attribute name
	 * @param rule The rule that derives that attribute's value
	 */
	protected void addDerivationRuleForAttribute(String beanClassName, String attributeName, AbstractRule rule) {
		
		String fullName = beanClassName + "/" + attributeName;
		
		// First check that a different rule doesn't already derive this attribute
		AbstractRule oldRule = derivationRules.get(fullName);
		if (oldRule != null && oldRule != rule) {
			log.error("Business logic method " + rule.getLogicGroup() + "#" + rule.getLogicMethodName() +
					" is attempting to derive the same attribute (" + rule.getBeanAttributeName() + ") as " +
					oldRule.getLogicGroup().getLogicClassName() + "#" + oldRule.getLogicMethodName() +
					". As a result, it will be ignored.");
			return;
		}
		
		// Is it already there?
		if (oldRule != null && oldRule.equals(rule)) {
			log.info("Business logic method " + rule.getLogicGroup() + "#" + rule.getLogicMethodName() +
					" was already known, ignoring");
			return;
		}
		
		derivationRules.put(fullName, rule);
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  RuleManager.java 780 2012-02-21 08:43:54Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 