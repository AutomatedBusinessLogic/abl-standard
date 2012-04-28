package com.autobizlogic.abl.metadata;

/**
 * Common interface for MetaAttribute and MetaRole.
 */
public interface MetaProperty {

	/**
	 * True if this property is an attribute, i.e. something whose value is a basic type such
	 * as integer, BigDecimal, String, etc...
	 */
	public boolean isAttribute();

	/**
	 * True if this property is a relationship (single- or multi-valued)
	 */
	public boolean isRelationship();

	/**
	 * True if this property is a multi-valued relationship.
	 */
	public boolean isCollection();
	
	/**
	 * Get the name of this property (role name in the case of a relationship)
	 */
	public String getName();
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 