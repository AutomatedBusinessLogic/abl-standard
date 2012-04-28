package com.autobizlogic.abl.event;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import org.hibernate.Transaction;

import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * Capture logic events in memory, in an easy-to-serialize format, up to a maximum number
 * of transactions. This is intended to be used by live transaction viewers. Transactions are kept in a queue,
 * with the oldest transactions dropping off once the queue is at capacity.
 */
public class AsyncLogicEventListener implements LogicListener {
	
	private LogicLogger logger = LogicLogger.getLogger(LoggerName.EVENT_LISTENER);

	/**
	 * This is where we keep the currently running transactions.
	 */
	private Map<String, Map<String, Object>> transactions = Collections.synchronizedMap(new HashMap<String, Map<String, Object>>());
	
	/**
	 * The committed transactions, with the oldest ones at the beginning.
	 */
	private LinkedList<Map<String, Object>> committedTransactions = new LinkedList<Map<String, Object>>();
	
	/**
	 * The keys for data in the nodes
	 */
	public final static String START_TIME = "START_TIME";
	public final static String END_TIME = "END_TIME";
	public final static String EVENTS = "EVENTS";
	public final static String EVENT_TYPE = "EVENT_TYPE";
	public final static String BEAN_CLASS = "BEAN_CLASS";
	public final static String BEAN_PK = "BEAN_PK";
	public final static String TITLE = "TITLE";
	public final static String EXEC_TIME = "EXEC_TIME";
	public final static String IDENT = "IDENT";
	public final static String USE_CASE_NAME = "USE_CASE_NAME";
	
	public final static String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
	public final static String LOGIC_METHOD_NAME = "LOGIC_METHOD_NAME";
	public final static String OLD_VALUE = "OLD_VALUE";
	public final static String QUALIFICATION = "QUALIFICATION";
	public final static String CHILD_ATTRIBUTE_NAME = "CHILD_ATTRIBUTE_NAME";
	public final static String PARENT_ATTRIBUTE_NAME = "PARENT_ATTRIBUTE_NAME";
	public final static String ROLE_NAME = "ROLE_NAME";
	public final static String LOGIC_RUNNER_TYPE = "LOGIC_RUNNER_TYPE";
	
	private final static int MAX_TX_AGE = 3000000; // Any transaction older than this will be considered stale and forgotten
	private final static int MAX_QUEUE_SIZE = 100; // How many transactions we can remember
	
	/**
	 * Get all committed transactions. The transactions
	 * are in reverse chronological order, with the most recent transaction first, oldest 
	 * transaction last.
	 */
	public List<Map<String, Object>> getAllTransactions() {
		cleanup();
		LinkedList<Map<String, Object>> result = new LinkedList<Map<String, Object>>();
		Iterator<Map<String, Object>> revIt = committedTransactions.descendingIterator();
		while (revIt.hasNext()) {
			Map<String, Object> tx = revIt.next();
//		for (Map<String, Object> tx : committedTransactions) {
			Map<String, Object> entry = new HashMap<String, Object>();
			entry.put(IDENT, tx.get(IDENT));
			entry.put(START_TIME, tx.get(START_TIME));
			entry.put(END_TIME, tx.get(END_TIME));
			entry.put(EXEC_TIME, tx.get(EXEC_TIME));
			entry.put(USE_CASE_NAME, tx.get(USE_CASE_NAME));
			result.add(entry);
		}
		return result;
	}
	
	public Map<String, Object> getTransaction(String txId) {
		if (txId == null || txId.trim().length() == 0) {
			logger.warn("Null txId passed for transaction");
			return null;
		}
		
		if (transactions.containsKey(txId))
			return transactions.get(txId);
		
		for (Map<String, Object> tx : committedTransactions) {
			if (txId.equals(tx.get(IDENT)))
				return tx;
		}
		
		return null;
	}
	
	/**
	 * Register a logic event
	 */
	@Override
	public void onLogicEvent(LogicEvent event) {
		
		Transaction tx = event.getContext().getSession().getTransaction();
		String txKey = "" + tx.hashCode();
		
		// If this is a commit, wrap up the transaction
		if (event.getEventType() == LogicEvent.EventType.BEFORE_COMMIT)
			return;
		if (event.getEventType() == LogicEvent.EventType.AFTER_COMMIT) {
			Map<String, Object> txRoot = transactions.get(txKey);
			if (txRoot == null) {
				logger.debug("Commit for unknown transaction was ignored");
				return;
			}
			
			txRoot.put(END_TIME, System.currentTimeMillis());
			txRoot.put(IDENT, txKey);
			transactions.remove(txKey);
			queueTransaction(txRoot);
			return;
		}
		
		// First, do we already know about this transaction? If not, then create its root node
		Map<String, Object> txRoot = transactions.get(txKey);
		if (txRoot == null) {
			txRoot = new HashMap<String, Object>();
			txRoot.put(START_TIME, System.currentTimeMillis());
			txRoot.put(EVENTS, new Vector<Map<String, Object>>());
			String useCaseName = LogicContext.getCurrentUseCaseName(event.getContext().getSession(), tx);
			if (useCaseName != null)
				txRoot.put(USE_CASE_NAME, useCaseName);
			transactions.put(txKey, txRoot);
		}
		
		// Create the new node for the event
		Map<String, Object> txNode = new HashMap<String, Object>();
		txNode.put(END_TIME, System.currentTimeMillis());
		txNode.put(EVENT_TYPE, event.getEventType().name());
		txNode.put(TITLE, event.getTitle());
		Serializable pk = event.getPersistentBean().getPk();
		txNode.put(BEAN_PK, pk.toString());
		txNode.put(BEAN_CLASS, event.getPersistentBean().getMetaEntity().getEntityName());
		
		txNode.put(EXEC_TIME, event.getExecutionTime());
		txNode.put(EVENTS, new Vector<Map<String, Object>>());
		if (event.getLogicContext() == null && (event.getEventType().equals(LogicEvent.EventType.BEFORE_COMMIT) || 
				event.getEventType().equals(LogicEvent.EventType.AFTER_COMMIT)))
			return;
		if (event.getLogicContext() == null)
			throw new RuntimeException("No LogicContext");
		int nestLevel = event.getLogicContext().getLogicNestLevel();
		
		// Find where this new node should go
		int lvl = 0;
		Map<String, Object> node = txRoot;
		while (lvl < nestLevel) {
			@SuppressWarnings("unchecked")
			Vector<Map<String, Object>> events = (Vector<Map<String, Object>>)node.get(EVENTS);
			if (events.isEmpty())
				throw new RuntimeException("Error in transaction levels");
			
			node = events.lastElement();
			lvl++;
		}
		@SuppressWarnings("unchecked")
		Vector<Map<String, Object>> events = (Vector<Map<String, Object>>)node.get(EVENTS);
		events.add(txNode);		

		// Event-specific details
		switch(event.eventType) {
			case AFTER_ACTION : {
				LogicAfterActionEvent evt = (LogicAfterActionEvent)event;
				txNode.put(ATTRIBUTE_NAME, evt.getActionRule().getBeanAttributeName());
				txNode.put(LOGIC_METHOD_NAME, evt.getActionRule().getLogicMethodName());
				break;
			}
			case AFTER_AGGREGATE : {
				LogicAfterAggregateEvent evt = (LogicAfterAggregateEvent)event;
				txNode.put(ATTRIBUTE_NAME, evt.getAggregateRule().getBeanAttributeName());
				txNode.put(LOGIC_METHOD_NAME, evt.getAggregateRule().getLogicMethodName());
				txNode.put(OLD_VALUE, evt.getOldValue());
				txNode.put(QUALIFICATION, evt.getAggregateRule().getQualification());
				break;
			}
			case AFTER_CONSTRAINT: {
				LogicAfterConstraintEvent evt = (LogicAfterConstraintEvent)event;
				txNode.put(ATTRIBUTE_NAME, evt.getConstraintRule().getBeanAttributeName());
				txNode.put(LOGIC_METHOD_NAME, evt.getConstraintRule().getLogicMethodName());
				break;
			}
			case AFTER_FORMULA: {
				LogicAfterFormulaEvent evt = (LogicAfterFormulaEvent)event;
				txNode.put(ATTRIBUTE_NAME, evt.getFormulaRule().getBeanAttributeName());
				txNode.put(LOGIC_METHOD_NAME, evt.getFormulaRule().getLogicMethodName());
				txNode.put(OLD_VALUE, evt.getOldValue());
				break;
			}
			case AFTER_PARENT_COPY: {
				LogicAfterParentCopyEvent evt = (LogicAfterParentCopyEvent)event;
				txNode.put(ATTRIBUTE_NAME, evt.getParentCopyRule().getBeanAttributeName());
				txNode.put(LOGIC_METHOD_NAME, evt.getParentCopyRule().getLogicMethodName());
				txNode.put(CHILD_ATTRIBUTE_NAME, evt.getParentCopyRule().getChildAttributeName());
				txNode.put(PARENT_ATTRIBUTE_NAME, evt.getParentCopyRule().getParentAttributeName());
				txNode.put(ROLE_NAME, evt.getParentCopyRule().getRoleName());
				break;
			}
			case BEFORE_ACTION: {
				LogicBeforeActionEvent evt = (LogicBeforeActionEvent)event;
				txNode.put(ATTRIBUTE_NAME, evt.getActionRule().getBeanAttributeName());
				txNode.put(LOGIC_METHOD_NAME, evt.getActionRule().getLogicMethodName());
				break;
			}
			case LOGIC_RUNNER : {
				LogicRunnerEvent evt = (LogicRunnerEvent)event;
				txNode.put(LOGIC_RUNNER_TYPE, evt.logicRunnerEventType.name());
				break;
			}
			default :
				throw new RuntimeException("Unknown event type : " + event.getEventType());
		}
	}

	/**
	 * Go over the currently running transactions and take out any that are too old.
	 * This is to eliminate any stalled or rolled back transactions.
	 */
	private void cleanup() {
		synchronized(transactions) {
			Set<String> toRemove = new HashSet<String>();
			for (String txKey : transactions.keySet()) {
				Map<String, Object> txRoot = transactions.get(txKey);
				long startTime = (Long)txRoot.get(START_TIME);
				if (System.currentTimeMillis() - startTime > MAX_TX_AGE) {
					logger.debug("Cleaning out stale transaction");
					toRemove.add(txKey);
				}
			}
			for (String txKey : toRemove)
				transactions.remove(txKey);
		}
	}
	
	/**
	 * Move a transaction record into the committedTransactions queue
	 */
	private void queueTransaction(Map<String, Object> txRoot) {
		synchronized(committedTransactions) {
			// First clean up any old transactions if needed
			while (committedTransactions.size() >= MAX_QUEUE_SIZE)
				committedTransactions.removeFirst();
			
			committedTransactions.add(txRoot);
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  AsyncLogicEventListener.java 145 2011-12-18 10:39:03Z max@automatedbusinesslogic.com $";
}
/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 