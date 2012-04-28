package com.autobizlogic.abl.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A container for PersistentBean objects, stored by entity name and primary key.
 */
public class PersistentBeanCache {
	private Map<String, Map<Serializable, PersistentBean>> cache = 
			new HashMap<String, Map<Serializable, PersistentBean>>();
	
	/**
	 * Add the given bean to this cache.
	 */
	public void addBean(PersistentBean bean) {
		Map<Serializable, PersistentBean> entityCache = cache.get(bean.getEntityName());
		if (entityCache == null) {
			entityCache = new HashMap<Serializable, PersistentBean>();
			cache.put(bean.getEntityName(), entityCache);
		}
		entityCache.put(bean.getPk(), bean);
	}
	
	/**
	 * Get the specified bean from this cache.
	 * @return Null if the bean is not in this cache.
	 */
	public PersistentBean getBean(String entityName, Serializable pk) {
		Map<Serializable, PersistentBean> entityCache = cache.get(entityName);
		if (entityCache == null)
			return null;
		return entityCache.get(pk);
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 