package com.autobizlogic.abl.event;

import com.autobizlogic.abl.rule.ConstraintRule;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.ConstraintFailure;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.session.LogicTransactionContext;

/**
 * The event fired after a Constraint rule has executed.
 */
public class LogicAfterConstraintEvent extends LogicEvent {

	private ConstraintRule constraintRule;
	private ConstraintFailure failure;

	public LogicAfterConstraintEvent(LogicTransactionContext context, LogicContext logicContext, ConstraintRule constraintRule, 
			PersistentBean persistentBean, ConstraintFailure failure) {
		super(context, logicContext, persistentBean, constraintRule.getBeanAttributeName());
		this.constraintRule = constraintRule;
		this.failure = failure;
		this.title = constraintRule.getLogicMethodName();
		eventType = EventType.AFTER_CONSTRAINT;
	}

	/**
	 * The constraint that has been fired.
	 */
	public ConstraintRule getConstraintRule() {
		return constraintRule;
	}
	
	/**
	 * Get the failure (if any) for this constraint event.
	 * @return Null if the constraint passed, otherwise the failure object.
	 */
	public ConstraintFailure getFailure() {
		return failure;
	}

	@Override
	public String toString() {
		String failureMsg = "";
		if (failure != null)
			failureMsg = " - failure: " + failure.toString();
		return super.toString() + " - " + constraintRule.getLogicGroup().getLogicClassName() + "." + 
			constraintRule.getLogicMethodName() + " on " + persistentBean + failureMsg;

	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicAfterConstraintEvent.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 