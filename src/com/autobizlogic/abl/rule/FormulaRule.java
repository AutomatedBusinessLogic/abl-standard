package com.autobizlogic.abl.rule;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.perf.PerformanceMonitor;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.event.LogicAfterFormulaEvent;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.MethodInvocationUtil;
import com.autobizlogic.abl.util.ObjectUtil;

/**
 * Define and execute a formula rule. This gets created by LogicGroup when it encounters a
 * @Formula annotation on a method.
 */
public class FormulaRule extends AbstractDependsOnRule {

	private boolean lazy = false;

	private boolean persistent = true;
	
	private String expression = null;
	
	private boolean skipDuringRecompute = false;
	
	private boolean pruning = true;

	protected static final JexlEngine jexlEngine = new JexlEngine();
	static {
		jexlEngine.setCache(512);
		jexlEngine.setSilent(false);
	}

	/**
	 * Construct a new formula
	 * @param logicGroup The logic group containing the logic for this formula
	 * @param logicMethodName The name of the method containing the logic for this formula
	 * @param beanAttributeName The name of the bean's attribute for which this formula is defined
	 */
	protected FormulaRule(LogicGroup logicGroup, String logicMethodName, String beanAttributeName) {
		this.logicGroup = logicGroup;
		this.logicMethodName = logicMethodName;
		this.setBeanAttributeName( beanAttributeName);
	}

	public boolean isLazy() {
		return lazy;
	}

	protected void setLazy(boolean b) {
		lazy = b;
	}

	public boolean isPersistent() {
		return persistent;
	}

	protected void setPersistent(boolean b) {
		persistent = b;
	}
	
	public String getExpression() {
		return expression;
	}
	
	protected void setExpression(String s) {
		expression = s;
	}
	
	public boolean isSkipDuringRecompute() {
		return skipDuringRecompute;
	}
	
	protected void setSkipDuringRecompute(boolean b) {
		this.skipDuringRecompute = b;
	}
	
	public boolean isPruning() {
		return pruning;
	}
	
	protected void setPruning(boolean b) {
		pruning = b;
	}

	/**
	 * Execute this formula method in a LogicObject
	 */
	public boolean execute(Object aLogicObject, LogicRunner aLogicRunner) {

		long startTime = System.nanoTime();
		boolean rtnDidExecute = false;
		String theLogicMethodName = getLogicMethodName();
		Object result = null;
		if (pruning && isFormulaPrunable(aLogicRunner)) {
			if (log.isDebugEnabled())
				log.debug ("Formula pruned " + getBeanAttributeName(), aLogicRunner);
			return false; // Note that we do not fire any event since the formula was not executed
		}
		
		PersistentBean currentDomainObject = aLogicRunner.getCurrentDomainObject();

		// Is the formula expressed in the annotation? If so, we evaluate it, and we call
		// the method as a courtesy but ignore its return value.
		if (expression != null && expression.trim().length() > 0) {
			result = evaluateExpression(currentDomainObject, aLogicRunner.getLogicContext());
			
			MetaAttribute metaAttribute = currentDomainObject.getMetaEntity().getMetaAttribute(getBeanAttributeName());
			Object convertedResult = ObjectUtil.convertToDataType(result, metaAttribute.getType());

			Object oldValue = currentDomainObject.get(getBeanAttributeName());

			// If the formula has the same value as the stored value, there is nothing to do, and the formula should not
			// be considered to have really fired.
			if ( ! ((oldValue == null && result == null) || (oldValue != null && result != null && oldValue.equals(result)))) {
				currentDomainObject.put(getBeanAttributeName(), convertedResult);
				rtnDidExecute = true;
				
				if ("true".equals(LogicConfiguration.getInstance().getProperty(PropertyName.INVOKE_FORMULA_METHODS))) {
					try { // Then call the method for debugging purposes, but ignore its return value
						MethodInvocationUtil.invokeMethodOnObject(aLogicObject, theLogicMethodName);
					}
					catch(Exception ex) {
						if (log.isWarnEnabled())
							log.warn("Formula method " + this.getLogicGroup().getLogicClassName() + "." +
								this.getLogicMethodName() + " threw an exception. This is not fatal because " +
								"the formula has an expression in its declaration, and therefore the method itself " +
								"is called purely as a courtesy, but this should be examined. The exception was: " + ex);
					}
				}
				
				firePostEvent(aLogicObject, aLogicRunner, oldValue, System.nanoTime() - startTime);
			}

			if (rtnDidExecute && log.isDebugEnabled())
				log.debug ("Formula changes attribute " + getBeanAttributeName() + " -> " + convertedResult + " on", 
						aLogicRunner);

			return rtnDidExecute;
		}
		
		// If the formula is not expressed in the annotation, it must be in the code
		
		if ( ! "true".equals(LogicConfiguration.getInstance().getProperty(PropertyName.INVOKE_FORMULA_METHODS))) {
			throw new RuntimeException(toString() + " does not have an annotation-based definition, but the ABL configuration " +
					"specifies that formula methods should not be invoked. If you wish to enable formula methods, you should change " +
					"your ABLConfig.properties file to indicate that with: invokeFormulaMethods=true");
		}
		
		try {
			result = MethodInvocationUtil.invokeMethodOnObject(aLogicObject, theLogicMethodName);
			if (result == null && getLogicGroup().isGroovy()) {
				if (sysLog.isDebugEnabled())
					sysLog.debug ("Groovy formula returns null, value unchanged " + getBeanAttributeName(), aLogicRunner);
				
			} else {
				if (result != null && result.equals(LogicContext.nullValue) && getLogicGroup().isGroovy()) {
					result = null;
				}
				currentDomainObject = aLogicRunner.getCurrentDomainObject();
				Object oldValue = currentDomainObject.get(getBeanAttributeName());

				// If the formula has the same value as the stored value, there is nothing to do, and the formula should not
				// be considered to have really fired.
				if ( ! ((oldValue == null && result == null) || 
						(oldValue != null && result != null && oldValue.equals(result)))) {
					currentDomainObject.put(getBeanAttributeName(), result);
					rtnDidExecute = true;
					firePostEvent(aLogicObject, aLogicRunner, oldValue, System.nanoTime() - startTime);
				}

				if (rtnDidExecute && log.isDebugEnabled())
					log.debug ("Formula changes attribute " + getBeanAttributeName() + " -> " + result + " on",  aLogicRunner);
			}
		}
		catch (Exception e) {
			throw new LogicException("Failure setting formula for : " + getBeanAttributeName() + 
					" on: " + currentDomainObject, e);
		}

		return rtnDidExecute;
	}
	
	/**
	 * Get the formula's value for a given object. This is used for recompute.
	 */
	public Object getFormulaValueForObject(Object aLogicObject, PersistentBean bean) {
		String theLogicMethodName = getLogicMethodName();
		Object result = null;
		
		if (expression != null && expression.trim().length() > 0) {
			try {
				result = evaluateExpression(bean, null);
			}
			catch(Exception ex) {
				throw new RuntimeException("Exception while computing formula expression " + theLogicMethodName + " on object " + aLogicObject, ex);
			}
		}
		else {
			try {
				result = MethodInvocationUtil.invokeMethodOnObject(aLogicObject, theLogicMethodName);
			}
			catch(Exception ex) {
				throw new RuntimeException("Exception while computing formula " + theLogicMethodName + " on object " + aLogicObject, ex);
			}
		}
		
		return result;
	}
	
	/**
	 * Translate the expression into a valid SQL expression
	 */
	public String getExpressionSQL() {

		if (expression == null)
			return null;
		String sqlClause = expression.replaceAll("\\|\\|", " OR ");
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
	 * If the formula is defined with an expression, this is where we evaluate the expression.
	 * @param aLogicObject
	 * @return
	 */
	private Object evaluateExpression(PersistentBean obj, LogicContext logicContext) {
		if (expression == null || expression.trim().length() == 0)
			return null;
		
		JexlContext context = new BeanMapContext(obj, logicContext, true);
		Expression expr = jexlEngine.createExpression(expression);
		Object res = null;
		try {
			res = expr.evaluate(context);
		}
		catch(Exception ex) {
			throw new LogicException("Error while evaluating expression : " + expression, ex);
		}
		
		if (res instanceof BeanMapContext.NullObjectMap)
			return null;
		
		return res;
	}

	/*
	public void computeValue(Object object, LogicTransactionContext context) {
		Class<?> cls = object.getClass();
		String methodName = getLogicMethodName();
		Object value = null;
		try {
			Method method = cls.getMethod(methodName, (Class<?>[])null);
			value = method.invoke(object, (Object[])null);
		} catch (Exception ex) {
			throw new RuntimeException("Exception while invoking formula - ", ex);
		}

		BeanMap beanMap = new BeanMap(object);
		beanMap.put(this.getBeanAttributeName(), value);
	}
	 */

	/**
	 * Fire the post event for this formula.
	 */
	protected void firePostEvent(Object aLogicObject, LogicRunner aLogicRunner, Object oldValue, long executionTime) {
		LogicAfterFormulaEvent evt = new LogicAfterFormulaEvent(aLogicRunner.getContext(), aLogicRunner.getLogicContext(),
				this, aLogicRunner.getCurrentDomainObject(), oldValue);
		evt.setExecutionTime(executionTime);
		LogicTransactionContext.fireEvent(evt);
		PerformanceMonitor.addRuleExecution(this, executionTime);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Formula ");
		sb.append(getLogicGroup().getMetaEntity().getEntityName());
		sb.append("#"); 
		sb.append(getLogicMethodName());
		if (expression != null && expression.trim().length() > 0) {
			sb.append(" [");
			sb.append(expression);
			sb.append("]");
		}
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  FormulaRule.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 