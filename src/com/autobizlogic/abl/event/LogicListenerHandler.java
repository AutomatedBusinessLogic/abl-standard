package com.autobizlogic.abl.event;

import java.util.List;

/**
 * The public interface for the objects that handle logic event listeners.
 */
public interface LogicListenerHandler {

	/**
	 * Register a listener.
	 * @param listener
	 */
	public void addLogicListener(LogicListener listener);
	
	/**
	 * Get the current list of listeners. The returned list is read-only.
	 * @return
	 */
	public List<LogicListener> getLogicListeners();
	
	/**
	 * Remove the given listeners from the list of active listeners. If the given object is not in the list,
	 * nothing happens.
	 */
	public void removeLogicListener(LogicListener listener);

	/**
	 * Remove all listeners that are instances of the given class (or any subclass thereof).
	 * If the list does not contain any such listeners, nothing happens.
	 */
	public void removeLogicListenersOfClass(Class<?> listenerClass);
	
	/**
	 * Check whether at least one listener of the given class is already present.
	 */
	public boolean hasLogicListenerOfClass(Class<?> listenerClass);
	
	/**
	 * Internal method -- fire an event. Do not use.
	 */
	public void fireEvent(LogicEvent evt);
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 