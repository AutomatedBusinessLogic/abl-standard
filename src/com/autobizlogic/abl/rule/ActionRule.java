package com.autobizlogic.abl.rule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.perf.PerformanceMonitor;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.event.LogicAfterActionEvent;
import com.autobizlogic.abl.event.LogicBeforeActionEvent;

/**
 * Represent and execute an action rule.
 */

public class ActionRule extends AbstractDependsOnWithVerbsRule {

	protected ActionRule(LogicGroup logicGroup, String logicMethodName) {
		this.logicGroup = logicGroup;
		this.logicMethodName = logicMethodName;
	}
	
	/**
	 * Execute the action method.
	 */
	public void execute(LogicRunner aLogicRunner) {
		long startTime = System.nanoTime();
		firePreEvent(aLogicRunner);
		try {
			String theLogicMethodName = getLogicMethodName();
			if (sysLog.isDebugEnabled())
				sysLog.debug ("Invoking Action Rule: " + theLogicMethodName, aLogicRunner);
			Class<?> logicClass = aLogicRunner.getLogicObject().getClass();
			Method actionMethod = logicClass.getMethod(theLogicMethodName, (Class<?>[])null); 
			actionMethod.invoke(aLogicRunner.getLogicObject(), (Object[])null); 
		}
		catch(InvocationTargetException ex) {
			throw new LogicException("Exception while executing action: " + logicMethodName + " on: " + aLogicRunner.getLogicObject(), ex.getCause());
		}
		catch(Exception ex) {
			throw new LogicException("Failure finding or executing action: " + logicMethodName + " on: " + aLogicRunner.getLogicObject(), ex);
		}
		firePostEvent(aLogicRunner, System.nanoTime() - startTime);		
	}
	
	/**
	 * Fire the post event for this formula.
	 */
	protected void firePostEvent(LogicRunner aLogicRunner, long executionTime) {
		LogicAfterActionEvent evt = new LogicAfterActionEvent(aLogicRunner.getContext(), aLogicRunner.getLogicContext(),
				this, aLogicRunner.getCurrentDomainObject());
		evt.setExecutionTime(executionTime);
		LogicTransactionContext.fireEvent(evt);
		PerformanceMonitor.addRuleExecution(this, executionTime);
	}
	
	/**
	 * Fire the post event for this formula.
	 */
	protected void firePreEvent(LogicRunner aLogicRunner) {
		LogicBeforeActionEvent evt = new LogicBeforeActionEvent(aLogicRunner.getContext(), aLogicRunner.getLogicContext(),
				this, aLogicRunner.getCurrentDomainObject());
		LogicTransactionContext.fireEvent(evt);
		
	}
	
	@Override
	public String toString() {
		return "Action " + super.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ActionRule.java 969 2012-03-20 09:13:11Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 