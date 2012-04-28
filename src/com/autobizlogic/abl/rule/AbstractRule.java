package com.autobizlogic.abl.rule;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.ProxyFactory;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.logic.BusinessLogicFactory;
import com.autobizlogic.abl.logic.BusinessLogicFactoryManager;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.logic.analysis.ClassDependency;
import com.autobizlogic.abl.logic.analysis.PropertyDependency;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
* Instances represent rule definitions, discovered on class load by the Rule Manager.  Each instance
* represents the rule definition properties, such as:
* <ol>
* <li>Dependencies</li>
* <li>Role references</li>
* </ol> 
* Rules are accessed via the LogicGroup.
 */
public abstract class AbstractRule {

	protected LogicLogger log = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	protected LogicLogger sysLog = LogicLogger.getLogger(LoggerName.SYSDEBUG);

	/**
	 * The LogicGroup containing this rule
	 */
	protected LogicGroup logicGroup;

	/**
	 * The name of the method implementing this formula
	 */
	protected String logicMethodName;


	/**
	 * The name of the bean's attribute for which this formula is defined
	 */
	private String beanAttributeName;
	
	/**
	 * All the properties that this rule depends on.
	 */
	private Set<RuleDependency> dependencies = new HashSet<RuleDependency>();

	/**
	 * Get the LogicGroup which defines this rule.
	 */
	public LogicGroup getLogicGroup() {
		return logicGroup;
	}

	/**
	 * Get the name of the method which defines this rule.
	 */
	public String getLogicMethodName() {
		return logicMethodName;
	}

	/**
	 * Get the name of the attribute (if any) for which this rule is defined.
	 * @return Null if this rule does not define an attribute (e.g. constraint)
	 */
	public String getBeanAttributeName() {
		return beanAttributeName;
	}

	/**
	 * Internal method. Set the name of the attribute for which this rule is defined.
	 * @param aName
	 */
	public void setBeanAttributeName(String aName) {
		beanAttributeName = aName;
	}
	
	/**
	 * Invoke the (often empty) method that defines this rule. In order to do this, we have to create
	 * an instance of the logic object. This is normally optimized by detecting that the method is empty,
	 * and not calling this method if it is.
	 */
	protected void invokeLogicMethod(PersistentBean currentParentState, PersistentBean priorParentState, LogicRunner childLogicRunner) {
		
		MetaModel metaModel = childLogicRunner.getContext().getMetaModel();
		LogicGroup theLogicGroup = RuleManager.getInstance(metaModel).getLogicGroupForEntity(currentParentState.getMetaEntity());
		if (theLogicGroup == null)
			throw new RuntimeException("Unable to find logic class for entity: " + currentParentState.getMetaEntity());
		
		BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();
		
		Object logicObject = businessLogicFactory.createLogicObjectForDomainObject(currentParentState);
		
		// Set the values of current bean, old bean and logic context
		Object currentObjectValue = null;
		String beanPropertyName = theLogicGroup.getCurrentBeanFieldName();
		if (beanPropertyName != null) {
			if (currentParentState.getMetaEntity().isPojo())
				currentObjectValue = currentParentState.getBean();
			else
				currentObjectValue = currentParentState;
			BeanUtil.setBeanProperty(logicObject, beanPropertyName, currentObjectValue);
		}

		Object oldObjectValue = null;
		String oldBeanPropName = theLogicGroup.getOldBeanFieldName();
		if (oldBeanPropName != null && priorParentState != null) {
			if (currentParentState.getMetaEntity().isPojo())
				oldObjectValue = ProxyFactory.getProxyForEntity(priorParentState);
			else
				oldObjectValue = priorParentState;
			BeanUtil.setBeanProperty(logicObject, oldBeanPropName, oldObjectValue);
		}
		
		// Create and set the LogicContext
		String contextFieldName = theLogicGroup.getContextFieldName();
		if (contextFieldName != null) {
			
			LogicContext childLogicContext = childLogicRunner.getLogicContext();
			LogicContext logicContext = businessLogicFactory.createLogicContext();
			logicContext.setSession(childLogicRunner.getContext().getSession());
	
			logicContext.setLogicNestLevel(childLogicContext.getLogicNestLevel());
			logicContext.setInitialVerb(childLogicContext.getInitialVerb());
			logicContext.setVerb(childLogicContext.getVerb());
			logicContext.setCurrentState(currentParentState);
			logicContext.setOldState(priorParentState);
			
			BeanUtil.setBeanProperty(logicObject, contextFieldName, logicContext);
		}
		
		try {
			Method countMethod = logicObject.getClass().getMethod(logicMethodName, (Class[])null);
			countMethod.invoke(logicObject, (Object[])null);
		}
		catch(Exception ex) {
			throw new RuntimeException("Exception thrown while executing logic method " + 
					theLogicGroup.getLogicClassName() + "." + logicMethodName, ex);
		}
	}
	
	/**
	 * Register that this rule has a dependency on the given property.
	 */
	public void addDependency(RuleDependency dep) {
		dependencies.add(dep);
	}
	
	/**
	 * Get the set of dependencies for this rule, in other words the set of attributes
	 * that this rule depends on.
	 */
	public Set<RuleDependency> getDependencies() {
		return dependencies;
	}
	
	/**
	 * Analyze the given expression and add the relevant dependencies.
	 * @param roleName If specified, use this as the role name in the dependencies. This is used
	 * for aggregates.
	 * @return The dependencies found in the expression
	 */
	public Set<PropertyDependency> addDependenciesFromExpression(String expr, 
			MetaEntity metaEntity, String roleName, String methodName) {
		Map<ClassDependency, List<PropertyDependency>> depends = 
				JexlExpressionAnalyzer.getDependenciesFromExpression(expr, 
						metaEntity, methodName);
		Set<PropertyDependency> propDepends = new HashSet<PropertyDependency>();
		for (List<PropertyDependency> deps : depends.values()) {
			propDepends.addAll(deps);
			for (PropertyDependency propDep : deps) {
				String theRoleName = propDep.getRoleName();
				if (roleName != null)
					theRoleName = roleName;
				RuleDependency ruleDep = new RuleDependency(propDep.getClassDependency().getClassName(),
						propDep.getPropertyName(), theRoleName);
				addDependency(ruleDep);
			}
		}
		
		return propDepends;
	}

	////////////////////////////////////////////////////////////////////////////////////
	// Menial stuff
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o == this)
			return true;
		if ( ! (o instanceof AbstractRule))
			return false;
		AbstractRule otherRule = (AbstractRule)o;
		return getLogicGroup().getLogicClassName().equals(otherRule.getLogicGroup().getLogicClassName()) &&
				getLogicMethodName().equals(otherRule.getLogicMethodName());
	}
	
	@Override
	public int hashCode() {
		return logicGroup.hashCode() + logicMethodName.hashCode();
	}
	
	@Override
	public String toString() {
		try {
			String logicGroupClassName = logicGroup != null ? logicGroup.getLogicClassName(): "null logicClassName";
			String logicGroupBeanClassName = logicGroup != null ? logicGroup.getLogicClassName(): "null logicClassName";
			return " - " + logicGroupClassName + "#" + logicMethodName + " -- bean: " + logicGroupBeanClassName;
		} catch (Exception e) {
			return "Rule " + beanAttributeName;
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  AbstractRule.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 