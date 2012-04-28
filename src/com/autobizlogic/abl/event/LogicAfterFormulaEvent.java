package com.autobizlogic.abl.event;

import com.autobizlogic.abl.rule.FormulaRule;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.session.LogicTransactionContext;

/**
 * An event fired after a formula has been computed. Note that it is possible to get a LogicBeforeFormulaEvent
 * without a corresponding LogicAfterFormulaEvent if the formula decided that it did not really need to
 * fire after all.
 */
public class LogicAfterFormulaEvent extends LogicEvent {
	
	private FormulaRule formulaRule;
	private Object oldValue;

	public LogicAfterFormulaEvent(LogicTransactionContext context, LogicContext logicContext, 
			FormulaRule formula, PersistentBean persistentBean, Object oldValue) {
		super(context, logicContext, persistentBean, formula.getBeanAttributeName());
		this.formulaRule = formula;
		this.oldValue = oldValue;
		eventType = EventType.AFTER_FORMULA;
	}

	/**
	 * The formula that was computed.
	 */
	public FormulaRule getFormulaRule() {
		return formulaRule;
	}
	
	/**
	 * The value of the formula's attribute before it was computed. This will by definition be null
	 * in the case of an insert.
	 */
	public Object getOldValue() {
		return oldValue;
	}
	
	@Override
	public String toString() {
		return super.toString() + " - " + formulaRule.getLogicGroup().getLogicClassName() + "." + 
			formulaRule.getLogicMethodName() + " on " + persistentBean;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicAfterFormulaEvent.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 