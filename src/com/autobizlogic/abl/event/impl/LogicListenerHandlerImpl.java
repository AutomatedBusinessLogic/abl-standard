package com.autobizlogic.abl.event.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.autobizlogic.abl.event.LogicEvent;
import com.autobizlogic.abl.event.LogicListener;
import com.autobizlogic.abl.event.LogicListenerHandler;

/**
 * Handles logic listeners. This class is instantiated by GlobalLogicEventHandler, and
 * should normally not be instantiated by anyone else.
 */
public class LogicListenerHandlerImpl implements LogicListenerHandler {
	
	private final static List<LogicListener> listeners = Collections.synchronizedList(new Vector<LogicListener>());
	
	@Override
	public void addLogicListener(LogicListener listener) {
		listeners.add(listener);
	}
	
	@Override
	public List<LogicListener> getLogicListeners() {
		return Collections.unmodifiableList(listeners);
	}
	
	@Override
	public void removeLogicListener(LogicListener listener) {
		listeners.remove(listener);
	}
	
	@Override
	public void removeLogicListenersOfClass(Class<?> listenerClass) {
		Set<LogicListener> toRemove = new HashSet<LogicListener>();
		for (LogicListener listener : listeners) {
			if (listenerClass.isAssignableFrom(listener.getClass()))
				toRemove.add(listener);
		}
		listeners.removeAll(toRemove);
	}
	
	@Override
	public boolean hasLogicListenerOfClass(Class<?> listenerClass) {
		for (LogicListener listener : listeners) {
			if (listenerClass.isAssignableFrom(listener.getClass()))
				return true;
		}
		return false;
	}
	
	@Override
	public void fireEvent(LogicEvent evt) {
		for (LogicListener listener : listeners) {
			listener.onLogicEvent(evt);
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicListenerHandlerImpl.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 