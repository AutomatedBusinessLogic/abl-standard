package com.autobizlogic.abl.event;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.event.ObjectEvent.EventType;
import com.autobizlogic.abl.util.ObjectUtil;

/**
 * This object is handed to listeners. It contains a record of all the objects that were modified, namely inserted,
 * updated or deleted, within the transaction. It is entirely possible that it may contain references to objects that
 * were in fact not modified, but inserts and deletes should be reliable.
 */
public class TransactionSummary {

	private Timestamp commitTimestamp;
	
	private String sessionId;
	
	private String transactionId;
	
	private Set<ObjectEvent> objectEvents = new HashSet<ObjectEvent>();
	
	// We keep the events in these buckets because it's common for the same object
	// to have multiple events of the same type within one transaction. Since we don't
	// want to burden the user with these, we simply keep the last one for each type.
	private Map<String, ObjectEvent> insertEvents = new HashMap<String, ObjectEvent>();
	private Map<String, ObjectEvent> updateEvents = new HashMap<String, ObjectEvent>();
	private Map<String, ObjectEvent> deleteEvents = new HashMap<String, ObjectEvent>();
	
	/**
	 * Get the time at which the transaction was committed.
	 */
	public Timestamp getCommitTimestamp() {
		return commitTimestamp;
	}

	public void setCommitTimestamp(Timestamp timestamp) {
		this.commitTimestamp = timestamp;
	}

	/**
	 * Get the (relatively) unique ID for the transaction's session
	 */
	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * Get the (relatively) unique ID for the transaction.
	 */
	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	/**
	 * Get all the object events for this transaction.
	 */
	public Set<ObjectEvent> getObjectEvents() {
		Set<ObjectEvent> result = new HashSet<ObjectEvent>();
		result.addAll(insertEvents.values());
		result.addAll(updateEvents.values());
		result.addAll(deleteEvents.values());
		return result;
	}

	/**
	 * Add an event to the set.
	 */
	public void addObjectEvent(PersistentBean bean, EventType eventType, Session session) {
		ObjectEvent objectEvent = new ObjectEvent(bean, eventType);
		String clsName = bean.getMetaEntity().getEntityName();
		String key = clsName + "#" + ObjectUtil.safeToString(bean.getPk());
		switch (eventType) {
			case INSERT :
				insertEvents.put(key, objectEvent);
				break;
			case UPDATE :
				updateEvents.put(key, objectEvent);
				break;
			case DELETE :
				deleteEvents.put(key, objectEvent);
				break;
		}
		objectEvents.add(objectEvent);
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  TransactionSummary.java 231 2011-12-29 08:34:50Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 