package com.autobizlogic.abl.event;

/**
 * The interface for anyone who wants to be notified of logic events.
 */
public interface LogicListener {
	
	/**
	 * Gets called whenever a logic event occurs.
	 * @param event A instance of a subclass of LogicEvent.
	 */
	public void onLogicEvent(LogicEvent event);
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 