package com.autobizlogic.abl.data;

import java.io.Serializable;
import java.util.Map;


import com.autobizlogic.abl.metadata.MetaEntity;

/**
 * Represents a persistent bean (regardless of its format -- POJO, Map, etc).
 */

public interface PersistentBean extends Map<String, Object> {
	
	/**
	 * Get the metaentity for this bean.
	 */
	public MetaEntity getMetaEntity();
	
	/**
	 * Get the primary key for this bean.
	 */
	public Serializable getPk();

	/**
	 * Get the entity name for this bean, equivalent to getMetaEntity().getEntityName()
	 */
	public String getEntityName();
	
	/**
	 * Get the underlying bean, assuming this is a POJO. Throws an exception if that's
	 * not the case.
	 */
	public Object getBean();

	/**
	 * Get the underlying map, assuming this is in fact a Map. Throws an exception
	 * if that's not the case.
	 * @return
	 */
	public Map<String, Object> getMap();
	
	/**
	 * Get the underlying Hibernate entity, which is either a Pojo or a Map.
	 * @return
	 */
	public Object getEntity();
	
	/**
	 * Returns true if the underlying bean is a Pojo.
	 */
	public boolean isPojo();
	
	/**
	 * Returns true if the underlying bean is a map.
	 */
	public boolean isMap();
	
	/**
	 * Make a shallow copy of this bean, with all attributes and parent entities but
	 * none of the collections.
	 */
	public PersistentBean duplicate();
	
	/**
	 * Get a compact representation of the bean, in the form EntityName[primary-key]
	 */
	public String toShortString();
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 