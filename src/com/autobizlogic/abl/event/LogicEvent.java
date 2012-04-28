package com.autobizlogic.abl.event;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.session.LogicTransactionContext;

public class LogicEvent {
	
	public enum EventType {
		AFTER_AGGREGATE,
		AFTER_FORMULA,
		BEFORE_ACTION,
		AFTER_ACTION,
		AFTER_CONSTRAINT,
		AFTER_PARENT_COPY,
		LOGIC_RUNNER,
		BEFORE_COMMIT,
		AFTER_COMMIT
	}
	
	protected LogicTransactionContext context;
	protected LogicContext logicContext;
	protected EventType eventType;
	protected PersistentBean persistentBean;
	protected String title;
	
	/**
	 * How long this event, and any children events, took to execute.
	 */
	protected long executionTime = 0;

	public LogicEvent(LogicTransactionContext context, LogicContext logicContext, 
			PersistentBean persistentBean, String title) {
		this.context = context;
		this.logicContext = logicContext;
		this.persistentBean = persistentBean;
		this.title = title;
	}

	public LogicTransactionContext getContext() {
		return context;
	}
	
	public LogicContext getLogicContext() {
		return logicContext;
	}
	
	public EventType getEventType() {
		return eventType;
	}

	public PersistentBean getPersistentBean() {
		return persistentBean;
	}

	public String getTitle() {
		return title;
	}
	
	public long getExecutionTime() {
		return executionTime;
	}

	public void setExecutionTime(long executionTime) {
		this.executionTime = executionTime;
	}

	@Override
	public String toString() {
		return "Logic event [" + eventType +" - " + title + "]";
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicEvent.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 