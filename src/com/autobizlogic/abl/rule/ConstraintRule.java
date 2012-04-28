package com.autobizlogic.abl.rule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.ConstraintFailure;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.InternalConstraintException;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.perf.PerformanceMonitor;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.event.LogicAfterConstraintEvent;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.text.LogicMessageFormatter;
import com.autobizlogic.abl.text.MessageName;
import com.autobizlogic.abl.util.MethodInvocationUtil;
import com.autobizlogic.abl.util.NodalPathUtil;

/**
 * Define and execute a constraint in the business logic.
 */
public class ConstraintRule extends AbstractDependsOnWithVerbsRule {
	
	protected String[] problemAttributes;
	
	private String expression = null;
	
	private String errorMessage = null;
	
	protected static final JexlEngine jexlEngine = new JexlEngine();
	static {
		jexlEngine.setCache(512);
		jexlEngine.setSilent(false);
	}
	
	//////////////////////////////////////////////////////////////////////

	protected ConstraintRule(LogicGroup logicGroup, String logicMethodName) {
		this.logicGroup = logicGroup;
		this.logicMethodName = logicMethodName;
	}
	
	/**
	 * Get the names of the attributes
	 * @return
	 */
	public String[] getProblemAttributes() {
		return problemAttributes;
	}

	/**
	 * Get the Jexl expression defined in the annotation, if present.
	 */
	public String getExpression() {
		return expression;
	}
	
	protected void setExpression(String s) {
		expression = s;
	}
	
	/**
	 * Get the error message defined in the annotation, if present.
	 */
	public String getErrorMessage() {
		return errorMessage;
	}
	
	protected void setErrorMessage(String s) {
		errorMessage = s;
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
	 * Get the error message defined in the annotation, formatted for the given bean.
	 * If no error message was defined in the annotation, a generic message is used.
	 */
	public String getFormattedErrorMessage(PersistentBean bean) {
		if (errorMessage == null)
			return LogicMessageFormatter.getMessage(MessageName.rule_Constraint_genericFailure, 
					new Object[]{getBeanAttributeName(), bean});
		
		if (errorMessage.indexOf('{') == -1)
			return errorMessage;
		
		StringBuffer formattedMsg = new StringBuffer();
		int braceIdx = 0;
		int startIdx = 0;
		int closeBraceIdx = 0;
		while (braceIdx != -1 && braceIdx < errorMessage.length() - 1) {
			braceIdx = errorMessage.indexOf('{', braceIdx);
			if (braceIdx == -1)
				break;
			closeBraceIdx = errorMessage.indexOf('}', braceIdx);
			if (braceIdx == -1) {
				break;
			}
			formattedMsg.append(errorMessage.substring(startIdx, braceIdx));
			String expr = errorMessage.substring(braceIdx + 1, closeBraceIdx);
			JexlEngine jexl = new JexlEngine();
			try {
				Expression e = jexl.createExpression(expr);
				BeanMapContext ctxt = new BeanMapContext(bean, null, false);
				Object result = e.evaluate(ctxt);
				if (result != null)
					formattedMsg.append(result.toString());
				else
					formattedMsg.append("<null>");
			}
			catch(Exception ex) {
				formattedMsg.append("<error>");
			}
			
			startIdx = closeBraceIdx + 1;
			braceIdx++;
		}
		if (closeBraceIdx > 0 && closeBraceIdx < errorMessage.length() - 1)
			formattedMsg.append(errorMessage.substring(closeBraceIdx + 1));
		
		return formattedMsg.toString();
	}
	
	public void execute(PersistentBean bean, LogicRunner aLogicRunner) {
		// Nothing to do here
	}

	/**
	 * Execute the constraint method.
	 */
	public ConstraintFailure executeConstraint(LogicRunner aLogicRunner) {
		
		// Users must not refer to transients aggregates on delete, else we must drop delete constraints like this...
		// We do not run constraints on deleted objects, because, well, they're deleted, and therefore
		// we often don't have access to e.g. their children (because they've been deleted too).
		//if (aLogicRunner.getVerb() == Verb.DELETE)
		//	return null;
		
		long startTime = System.nanoTime();
		ConstraintFailure failure = null;
		try {
			if (expression != null && expression.trim().length() > 0) {
				executeDeclaredConstraint(aLogicRunner.getLogicObject(), aLogicRunner.getCurrentDomainObject(), 
						false, aLogicRunner.getLogicContext());
			}
			else {
				String theLogicMethodName = getLogicMethodName();
				Class<?> logicClass = aLogicRunner.getLogicObject().getClass();
				Method constraintMethod = logicClass.getMethod(theLogicMethodName, (Class<?>[])null);
				if (constraintMethod == null)
					throw new RuntimeException("Unable to find constraint method " + theLogicMethodName + " in class " + logicClass.getName());
				constraintMethod.invoke(aLogicRunner.getLogicObject(), (Object[])null);
			}
		}
		catch (NoSuchMethodException e) {
			throw new LogicException("Failure finding / executing constraint: " + logicMethodName + " on: " + aLogicRunner.getLogicObject(), e);
		}
		catch (IllegalAccessException e) {
			throw new LogicException("Failure finding or executing constraint: " + logicMethodName + " on: " + aLogicRunner.getLogicObject(), e);
		}
		catch (InvocationTargetException e) {  // this is the exception we get for failed constraints
			Throwable cause = e.getCause();
			if (cause != null && (cause instanceof InternalConstraintException)) {
				InternalConstraintException ice = (InternalConstraintException)cause;
				String[] atts = ice.getProblemAttributes();
				if (atts == null)
					atts = problemAttributes;
				failure = new ConstraintFailure(ice.getMessage(), atts);
				failure.setLogicClassName(aLogicRunner.getLogicObject().getClass().getName());
				failure.setConstraintName(getLogicMethodName());
				failure.setProblemPk(aLogicRunner.getCurrentDomainObject().getPk());
				if (log.isDebugEnabled()) {
					log.debug("Constraint failure: " + failure.getLogicClassName() + "." + 
							failure.getConstraintName() + " for object [" + failure.getProblemPk() + "]");
				}
			}
			else {
				throw new LogicException("Failure finding or executing constraint: " + 
						logicMethodName + " on: " + aLogicRunner.getLogicObject() + 
						". A common cause is throwing an exception in a constraint " +
						"without using ConstraintFailure.failConstraint().", e);
			}
		}
		catch (Exception e) {
			throw new LogicException("Failure finding or executing constraint: " + logicMethodName + " on: " + aLogicRunner.getLogicObject(), e);
		}
		
		firePostEvent(aLogicRunner.getLogicObject(), aLogicRunner, failure, System.nanoTime() - startTime);
		
		return failure;
	}
	
	/**
	 * Execute the expression defined in the annotation.
	 * @param aLogicObject The logic object for the bean
	 * @param bean The bean itself
	 * @param skipMethodIfPossible If true, and the constraint is an expression, do not execute the method.
	 * @throws InvocationTargetException If the expression did not evaluate successfully
	 */
	private void executeDeclaredConstraint(Object aLogicObject, PersistentBean bean, 
			boolean skipMethodIfPossible, LogicContext logicContext) throws InvocationTargetException {
		
		Object result = evaluateExpression(bean, logicContext);
		if (result == null)
			throw new RuntimeException("Constraint " + this.getLogicGroup().getLogicClassName() + "." +
					this.getLogicMethodName() + " evaluated to null. A constraint must evaluate to true or false.");
		if ( ! (result instanceof Boolean))
			throw new RuntimeException("Constraint " + this.getLogicGroup().getLogicClassName() + "." +
					this.getLogicMethodName() + " evaluated to a non-boolean value. A constraint must evaluate to true or false.");
		
		if ( ! skipMethodIfPossible) {
			try { // Then call the method for debugging purposes, but ignore its return value
				MethodInvocationUtil.invokeMethodOnObject(aLogicObject, logicMethodName);
			}
			catch(Exception ex) {
				log.warn("Constraint method " + this.getLogicGroup().getLogicClassName() + "." +
						this.getLogicMethodName() + " threw an exception. This is not fatal because " +
						"the constraint has an expression in its declaration, and therefore the method itself " +
						"is called only for debugging convenience, but this should be examined. The exception was: " + ex);
			}
		}

		// If the constraint simply evaluated to false...
		if ( ! ((Boolean)result)) {
			String msg = "Constraint expression " + getLogicGroup().getLogicClassName() + "." + getLogicMethodName() + " evaluated to false.";
			if (errorMessage != null) 
				msg = getFormattedErrorMessage(bean);
			throw new InvocationTargetException(new InternalConstraintException(msg));
		}		
}
	
	/**
	 * Execute the constraint method, but without a full context. This is used for post-facto checking.
	 * @param aLogicObject The LogicObject currently in use
	 * @param bean The persistent bean for which to execute this constraint.
	 * @return Null if the constraint succeeded, otherwise a ConstraintFailure with the relevant information.
	 */
	public ConstraintFailure executeConstraintForObject(Object aLogicObject, PersistentBean bean) {
		ConstraintFailure failure = null;
		
		Class<?> logicClass = aLogicObject.getClass();
		String theLogicMethodName = getLogicMethodName();
		try {
			if (expression != null && expression.trim().length() > 0) {
				executeDeclaredConstraint(aLogicObject, bean, true, null);
			}
			else {
				Method constraintMethod = logicClass.getMethod(theLogicMethodName, (Class<?>[])null); 
				constraintMethod.invoke(aLogicObject, (Object[])null); 
			}
		}
		catch (NoSuchMethodException e) {
			throw new LogicException("Failure finding / executing constraint: " + theLogicMethodName + " on: " + aLogicObject, e);
		}
		catch (IllegalAccessException e) {
			throw new LogicException("Failure finding or executing constraint: " + theLogicMethodName + " on: " + aLogicObject, e);
		}
		catch (InvocationTargetException e) {  // this is the exception we get for failed constraints
			Throwable cause = e.getCause();
			String causeMsg = cause == null ? "System error - unknown message" : cause.getMessage();
			failure = new ConstraintFailure(causeMsg, problemAttributes);
		}
		catch (Exception e) {
			throw new LogicException("Failure finding or executing constraint: " + theLogicMethodName + " on: " + aLogicObject, e);
		}
		
		return failure;
	}
	
	/**
	 * If the formula is defined with an expression, this is where we evaluate the expression.
	 * @param bean The PersistentBean for which the expression must be evaluated
	 * @return The result of the expression evaluation.
	 */
	private Object evaluateExpression(PersistentBean bean, LogicContext logicContext) {
		if (expression == null || expression.trim().length() == 0)
			return null;
		
		JexlContext context = new BeanMapContext(bean, null, false);
		Expression expr;
		try {
			expr = jexlEngine.createExpression(expression);
		} catch(Exception ex) {
			ex.printStackTrace();
			throw new LogicException("Error while creating expression : " + expression + " for constraint " + getLogicMethodName() + " in class " + getLogicGroup().getLogicClassName(), ex);
		}
		Object res = null;
		try {
			res = expr.evaluate(context);
		} catch(Exception ex) {
			ex.printStackTrace();
			throw new LogicException("Error while evaluating expression : " + expression, ex);
		}
		
		return res;
	}

	/**
	 * Fire the post event for this constraint.
	 */
	protected void firePostEvent(Object aLogicObject, LogicRunner aLogicRunner, 
			ConstraintFailure failure, long executionTime) {
		
		LogicAfterConstraintEvent evt = new LogicAfterConstraintEvent(aLogicRunner.getContext(), aLogicRunner.getLogicContext(),
				this, aLogicRunner.getCurrentDomainObject(), failure);
		evt.setExecutionTime(executionTime);
		LogicTransactionContext.fireEvent(evt);
		PerformanceMonitor.addRuleExecution(this, executionTime);
	}
	
	///////////////////////////////////////////////////////////////////////////////////////

	@Override
	public String toString() {
		String s = "Constraint " + NodalPathUtil.getNodalPathLastName(logicGroup.getLogicClassName()) + "#" + logicMethodName;
		if (this.getExpression() != null && getExpression().trim().length() > 0)
			s += " [" + getExpression() + "]";
		if (problemAttributes != null) {
			s += " - problem attributes:";
			for (String att : problemAttributes)
				s += att + ", ";
		}
		return s;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ConstraintRule.java 1207 2012-04-19 22:33:25Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 