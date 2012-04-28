package com.autobizlogic.abl.event;

import java.io.Serializable;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;

/**
 * Represents an event that happened to a specific persistent object.
 */
public class ObjectEvent {

	public static enum EventType {
		INSERT,
		UPDATE,
		DELETE
	}
	
	private EventType eventType;
	
	private String entityName;
	
	private Serializable primaryKey;
	
	private PersistentBean currentValues;

	protected ObjectEvent(PersistentBean bean, EventType eventType) {
		entityName = bean.getEntityName();
		primaryKey = bean.getPk();
		this.eventType = eventType;
		
		currentValues = HibPersistentBeanFactory.copyPersistentBean(bean);
	}

	/**
	 * Get the type of the event, namely whether this was an insert, an update or a delete.
	 */
	public EventType getEventType() {
		return eventType;
	}

	public void setEventType(EventType eventType) {
		this.eventType = eventType;
	}

	/**
	 * Get the name of the entity of the object, for instance "com.acme.widgets.Rocket"
	 * or "Customer"
	 */
	public String getClassName() {
		return entityName;
	}

	/**
	 * Get the primary key.
	 */
	public Serializable getPrimaryKey() {
		return primaryKey;
	}
	
	/**
	 * Get the state of the entity at the time of the event.
	 */
	public PersistentBean getCurrentValues() {
		return currentValues;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////

	@Override
	public String toString() {
		return "" + eventType + " - " + entityName + "[" + primaryKey + "]";
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ObjectEvent.java 168 2011-12-19 22:42:23Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 