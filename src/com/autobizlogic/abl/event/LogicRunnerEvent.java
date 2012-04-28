package com.autobizlogic.abl.event;

import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.NodalPathUtil;

/**
 * A special kind of event for phase changes in a LogicRunner.
 */
public class LogicRunnerEvent extends LogicEvent {
	
	public enum LogicRunnerEventType {
		BEGININSERT,
		BEGINUPDATE,
		BEGINDELETE,
		BEGINCASCADE,
		ENDCASCADE,
		END
	}

	LogicRunnerEventType logicRunnerEventType = null;
	
	/**
	 * Creates a LogicRunnerEvent of specified subtype.
	 * 
	 * @param context The current transaction context
	 * @param aLogicContext The current logic context
	 * @param aLogicRunnerEventType The type of event to create
	 */
	public LogicRunnerEvent(LogicTransactionContext context, LogicContext aLogicContext, 
			LogicRunnerEventType aLogicRunnerEventType) {
		
		super(context, aLogicContext, aLogicContext.getCurrentState(), null);
		eventType = EventType.LOGIC_RUNNER;
		logicRunnerEventType = aLogicRunnerEventType;
		switch (aLogicRunnerEventType) {
			case BEGININSERT : title = "Insert: "; break;
			case BEGINUPDATE : title = "Update: "; break;
			case BEGINDELETE : title = "Delete: "; break;
			case BEGINCASCADE : title = "Cascade: "; break;
			case ENDCASCADE : title = "Cascade complete"; break;
			case END : title = "End"; break;
			default:
				throw new LogicException("Unexpected LogicRunnerEventType:" + aLogicRunnerEventType);
		}
		
		if (aLogicRunnerEventType != LogicRunnerEventType.END) {
			String entityName =  aLogicContext.getCurrentState().getEntityName();
			entityName = NodalPathUtil.getNodalPathLastName(entityName);
			title += entityName;
		}
	}
	
	/**
	 * Get the type of this event.
	 */
	public LogicRunnerEventType getLogicRunnerEventType() {
		return logicRunnerEventType;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public String toString() {
		return super.toString() + " - LogicRunner[" + logicRunnerEventType + "]";
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicRunnerEvent.java 145 2011-12-18 10:39:03Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 