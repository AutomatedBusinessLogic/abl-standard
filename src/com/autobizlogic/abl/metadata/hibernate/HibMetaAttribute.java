package com.autobizlogic.abl.metadata.hibernate;

import com.autobizlogic.abl.metadata.MetaAttribute;

/**
 * The implementation of MetaAttribute for Hibernate.
 */
public class HibMetaAttribute implements MetaAttribute {

	private String name;
	private Class<?> type;
	private boolean isTransient;
	
	/**
	 * You should retrieve instances of this type only from a MetaEntity.
	 */
	protected HibMetaAttribute(String name, Class<?> type, boolean isTransient) {
		this.name = name;
		this.type = type;
		this.isTransient = isTransient;
	}
	
	/**
	 * Get the name of this attribute.
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Get the type of this attribute, which may be a basic type, e.g. int or boolean.
	 */
	@Override
	public Class<?> getType() {
		return type;
	}
	
	@Override
	public boolean isAttribute() {
		return true;
	}
	
	@Override
	public boolean isRelationship() {
		return false;
	}
	
	@Override
	public boolean isCollection() {
		return false;
	}
	
	@Override
	public boolean isTransient() {
		return isTransient;
	}
	
	@Override
	public String toString() {
		return "Meta attribute " + name + " [type " + type.getName() + "]";
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibMetaAttribute.java 599 2012-01-25 15:08:12Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 