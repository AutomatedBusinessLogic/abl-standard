package com.autobizlogic.abl.event.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.autobizlogic.abl.event.TransactionSummary;
import com.autobizlogic.abl.event.TransactionSummaryListener;
import com.autobizlogic.abl.event.TransactionSummaryListenerHandler;

public class TransactionSummaryListenerHandlerImpl implements TransactionSummaryListenerHandler {

	protected List<TransactionSummaryListener> listeners = Collections.synchronizedList(new Vector<TransactionSummaryListener>());
	/**
	 * Register a listener.
	 * @param listener
	 */
	@Override
	public void addListener(TransactionSummaryListener listener) {
		listeners.add(listener);
	}
	
	/**
	 * Get the current list of listeners. The returned list is read-only.
	 * @return
	 */
	@Override
	public List<TransactionSummaryListener> getListeners() {
		return Collections.unmodifiableList(listeners);
	}
	
	/**
	 * Remove the given listeners from the list of active listeners. If the given object is not in the list,
	 * nothing happens.
	 */
	@Override
	public void removeListener(TransactionSummaryListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Remove all listeners that are instances of the given class (or any subclass thereof).
	 * If the list does not contain any such listeners, nothing happens.
	 */
	@Override
	public void removeListenersOfClass(Class<?> listenerClass) {
		Set<TransactionSummaryListener> toRemove = new HashSet<TransactionSummaryListener>();
		for (TransactionSummaryListener listener : listeners) {
			if (listenerClass.isAssignableFrom(listener.getClass()))
				toRemove.add(listener);
		}
		listeners.removeAll(toRemove);
	}
	
	/**
	 * Check whether at least one listener of the given class is already present.
	 */
	@Override
	public boolean hasListenerOfClass(Class<?> listenerClass) {
		for (TransactionSummaryListener listener : listeners) {
			if (listenerClass.isAssignableFrom(listener.getClass()))
				return true;
		}
		return false;
	}
	
	/**
	 * Internal method -- publish a transaction summary. Do not use.
	 */
	@Override
	public void publishSummary(TransactionSummary summary) {
		if (listeners.size() == 0)
			return;
		
		for (TransactionSummaryListener listener : listeners) {
			listener.transactionCommitted(summary);
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  TransactionSummaryListenerHandlerImpl.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 