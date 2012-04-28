package com.autobizlogic.abl.event;

import com.autobizlogic.abl.session.LogicTransactionContext;

/**
 * The event fired after a Commit has executed.
 */
public class LogicAfterCommitEvent extends LogicEvent {
	
	public enum CommitFailure {
		CONSTRAINTFAILURE, 
		EXCEPTION
	}

	public LogicAfterCommitEvent(LogicTransactionContext context) {
		super(context, null, null, null);
		eventType = EventType.AFTER_COMMIT;
		title = "committed";
	}
	public LogicAfterCommitEvent(LogicTransactionContext context, CommitFailure aCommitFailure) {
		super(context, null, null, null);
		eventType = EventType.AFTER_COMMIT;
		if (aCommitFailure == CommitFailure.CONSTRAINTFAILURE) 
			title = "Constraint Failure";
		else if (aCommitFailure == CommitFailure.EXCEPTION)
			title = "Exception Encountered";
	}
	
	@Override
	public String toString() {
		return super.toString() + " - AFTER_COMMIT";

	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicAfterCommitEvent.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 