package com.autobizlogic.abl.rule;

import java.math.BigDecimal;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.phase.AdjustAllParents;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.perf.PerformanceMonitor;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.event.LogicAfterAggregateEvent;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.NumberUtil;

/**
 * The superclass for SumRule, CountRule and MinMaxRule.
 */
public abstract class AbstractAggregateRule extends AbstractRule {

	protected String roleName;
	protected String qualification;
	protected static final JexlEngine jexlEngine = new JexlEngine();
	static {
		jexlEngine.setCache(512);
		jexlEngine.setSilent(false);
	}
	protected boolean inMemory = false;
	protected boolean persistent = true;
	protected boolean persistenceDetermined = false;

	/**
	 * True if the method defining this aggregate is empty
	 */
	protected boolean noCode;

	/**
	 * role for aggregate (eg, "orders" for a customer)
	 */
	protected MetaRole role;


	public enum AggregateAlgorithm {
		ADJUST, 		// used when aggregate is not transient, and not recompute
		MEMORY,		// used for small collections, or recompute
		SQL				// used for large collections, or recompute
	}
	
	@SuppressWarnings("unused")
	private AggregateAlgorithm aggregateAlgorithm;

	/**
	 * computed based on aggregate's annotation, and recompute:
	 *   - annotation signals small/large collection
	 *   - property file signals recompute
	 */
	@SuppressWarnings("unused")
	private String algorithm;


	protected AbstractAggregateRule(LogicGroup logicGroup, String logicMethodName, String roleName, 
			String qualification, String beanAttributeName) {
		
		this.logicGroup = logicGroup;
		this.logicMethodName = logicMethodName;
		this.roleName = roleName;
		this.role = logicGroup.getMetaEntity().getMetaRole(roleName);
		if (this.role == null)
			throw new RuntimeException("Entity " + logicGroup.getMetaEntity().getEntityName() +
					" does not have a role named " + roleName);
		this.qualification = qualification;

		fixQualification();

		this.setBeanAttributeName(beanAttributeName);
	}

	/**
	 * Subclass responsibility: read / alter aBusinessLogicExecutor.adjustedParentDomainObject 
	 * if aggregate warranted (eg, chg summed field, qual condition, pk)
	 * 
	 * @param aCurrentChild
	 * @param aPriorChild
	 * @return adjusted Parent Domain Object (iff altered)
	 */
	public abstract void adjustedParentDomainObject(AdjustAllParents aParentAdjustments);

	/**
	 * Get the child role through which this dependency runs.
	 */
	public String getRoleName() {
		return roleName;
	}

	/**
	 * Get the role which the aggregate spans.
	 */
	public MetaRole getRole() {
		return role;
	}


	public void setRole(MetaRole role) {
		this.role = role;
	}

	public boolean getInMemory() {
		return inMemory;
	}

	protected void setInMemory(boolean b) {
		inMemory = b;
	}

	/**
	 * Determine whether this aggregate is for a transient attribute or not.
	 * @return True if the attribute is not transient.
	 */
	@SuppressWarnings("static-method")
	public boolean isPersistent() {
		//if (persistenceDetermined)
		//	return persistent;

		return true;
		
/* COMMENTED OUT until the whole non-persistent attribute question is better resolved.
		// Get the get/is method of the persistent bean and determine whether it has the @Transient annotation.
		String beanClassName = getLogicGroup().getMetaEntity().getEntityName();
		String attName = getBeanAttributeName();
		try {
			Class<?> beanClass = ClassLoaderManager.getInstance().getClassFromName(beanClassName);
			String getterName = "get" + Character.toUpperCase(attName.charAt(0)) + attName.substring(1);
			Method getter = null;
			try {
				getter = beanClass.getMethod(getterName);
			}
			catch(NoSuchMethodException nsme) {
				getterName = "is" + Character.toUpperCase(attName.charAt(0)) + attName.substring(1);
				getter = beanClass.getMethod(getterName);
			}
			persistenceDetermined = true;
			persistent = getter.getAnnotation(Transient.class) == null;
			return persistent;
		}
		catch(Exception ex) {
			throw new RuntimeException("Unable to determine whether attribute " + attName + " of class " + beanClassName + " is persistent", ex);
		}
*/
	}

	protected void setPersistent(boolean b) {
		persistent = b;
	}

	/**
	 * Tells the rule that the method defining it has no code, and therefore should not be invoked.
	 * This is an internal method and should not be called.
	 */
	public void setNoCode(boolean b) {
		noCode = b;
	}

	/**
	 * Get the qualification, if any. This is normally not intended to be used directly.
	 * The qualification can be evaluated with runQualificationForBean, or the qualification's
	 * SQL equivalent can be obtained using getQualificationSQL.
	 */
	public String getQualification() {
		return qualification;
	}

	/**
	 * Execute the where clause for a given bean.
	 * @param bean The bean in question
	 * @return True if the bean satisfies the clause.
	 */
	public boolean runQualificationForBean(PersistentBean bean) {

		if (bean == null)
			throw new LogicException("Internal error: cannot evaluate expression on null object state on: " + this);
		// TODO - Optimization opportunity: clearly the expression could be parsed
		// and stored as part of the rule object, thus avoiding the creation
		// of the Expression object for every execution.
		if (qualification == null || "".equals(qualification.trim()))
			return Boolean.TRUE;
		
		// Replace all single = with ==
		qualification = qualification.replaceAll("([^=!<>])=([^=])", "$1==$2");
		
		JexlContext context = new BeanMapContext(bean, null, false);
		Expression expr = jexlEngine.createExpression(qualification);
		Object res = null;
		try {
			res = expr.evaluate(context);
		} catch(Exception ex) {
			ex.printStackTrace();
			throw new LogicException("Error while evaluating expression : " + qualification, ex);
		}
		if (res == null || ( ! (res instanceof Boolean)))
			throw new RuntimeException("Expression should return a boolean");
		return (Boolean)res;
		
	}

	/**
	 * Translate the expression into a valid SQL where clause
	 */
	public String getQualificationSQL() {

		if (qualification == null)
			return null;
		String sqlClause = qualification.replaceAll("\\|\\|", " OR ");
		sqlClause = sqlClause.replaceAll("&&", " AND ");
		sqlClause = sqlClause.replaceAll("==null", " IS NULL ");
		sqlClause = sqlClause.replaceAll("== null", " IS NULL ");
		sqlClause = sqlClause.replaceAll("!=null", " IS NOT NULL ");
		sqlClause = sqlClause.replaceAll("!= null", " IS NOT NULL ");
		sqlClause = sqlClause.replaceAll("==", "=");
		sqlClause = sqlClause.replaceAll("!=", "<>");
		sqlClause = sqlClause.replaceAll("!", " NOT ");
		return sqlClause;
	}

	/**
	 * When a new instance is created, we check the qualification for some common mistakes
	 */
	//private static final Pattern equalsPattern = Pattern.compile("", Pattern.CASE_INSENSITIVE);
	private void fixQualification() {
		if (qualification == null)
			return;

		// Replace any = with ==
		qualification = qualification.replaceAll("([^=<>!])=([^=])", "$1==$2");
	}

	/**
	 * Fire the post event for this aggregate.
	 */
	protected void firePostEvent(Object aLogicObject, LogicRunner aLogicRunner, PersistentBean bean, Number oldValue, long executionTime) {
		LogicAfterAggregateEvent evt = new LogicAfterAggregateEvent(aLogicRunner.getContext(), aLogicRunner.getLogicContext(), 
				this, bean, oldValue);
		evt.setExecutionTime(executionTime);
		LogicTransactionContext.fireEvent(evt);
		PerformanceMonitor.addRuleExecution(this, executionTime);
	}
	
	/**
	 * Get the value of a numeric attribute as a BigDecimal.
	 * @param anObjectState The object state from which to retrieve the value
	 * @param aPropertyName The name of the property to retrieve
	 * @return Zero if the attribute is null, otherwise the value of the attribute.
	 */
	protected static BigDecimal getObjectPropertyAsBigDecimal(PersistentBean anObjectState, String aPropertyName) {
		return getObjectPropertyAsBigDecimal(anObjectState, aPropertyName, false);
	}

	/**
	 * Get the value of a numeric attribute as a big decimal. This version allows the return of null
	 * if nullAllowed is true.
	 */
	protected static BigDecimal getObjectPropertyAsBigDecimal(PersistentBean anObjectState, String aPropertyName, boolean nullAllowed) {
		if (anObjectState == null)
			throw new RuntimeException("Cannot get property from null object");
		Number value = (Number)anObjectState.get(aPropertyName);
		if (value == null) {
			if (nullAllowed)
				return null;
			return BigDecimal.ZERO;
		}
		return (BigDecimal)NumberUtil.convertNumberToType(value, BigDecimal.class);
	}

	////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public String toString() {
		return super.toString() + ", role: " + roleName + ", where: " + qualification;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  AbstractAggregateRule.java 1207 2012-04-19 22:33:25Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 