package com.autobizlogic.abl.event;

import com.autobizlogic.abl.rule.AbstractAggregateRule;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.BeanMap;

/**
 * The event fired after an aggregate rule (sum or count) has executed.
 */
public class LogicAfterAggregateEvent extends LogicEvent {

	private AbstractAggregateRule aggregateRule;
	private Number oldValue;

	public LogicAfterAggregateEvent(LogicTransactionContext context, LogicContext logicContext, 
			AbstractAggregateRule aggregateRule, PersistentBean persistentBean, Number oldValue) {
		super(context, logicContext, persistentBean, aggregateRule.getBeanAttributeName());
		this.aggregateRule = aggregateRule;
		this.oldValue = oldValue;
		eventType = EventType.AFTER_AGGREGATE;
	}

	/**
	 * The aggregate that is being computed.
	 */
	public AbstractAggregateRule getAggregateRule() {
		return aggregateRule;
	}
	
	/**
	 * Get the value of the aggregate before it was recomputed.
	 */
	public Number getOldValue() {
		return oldValue;
	}
	
	@Override
	public String toString() {
		BeanMap beanMap = new BeanMap(persistentBean);
		String attName = aggregateRule.getBeanAttributeName();
		String oldValStr = "<null>";
		if (oldValue != null)
			oldValStr = oldValue.toString();
		String newValStr = "<null>";
		Object newVal = beanMap.get(attName);
		if (newVal != null)
			newValStr = newVal.toString();
		return super.toString() + " - " + aggregateRule.getLogicGroup().getLogicClassName() + "." + 
			aggregateRule.getLogicMethodName() + " on " + persistentBean + " - " + attName + 
			" adjusted from " + oldValStr + " to " + newValStr;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicAfterAggregateEvent.java 719 2012-02-08 11:34:50Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 