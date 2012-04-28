package com.autobizlogic.abl.event;

import com.autobizlogic.abl.session.LogicTransactionContext;

/**
 * The event fired before a commit is executed.
 */
public class LogicBeforeCommitEvent extends LogicEvent {

	public LogicBeforeCommitEvent(LogicTransactionContext context) {
		super(context, null, null, null);
		eventType = EventType.BEFORE_COMMIT;
		title = "Commit Logic";
	}
	
	@Override
	public String toString() {
		return super.toString() + " - BEFORE_COMMIT";

	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicBeforeCommitEvent.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 