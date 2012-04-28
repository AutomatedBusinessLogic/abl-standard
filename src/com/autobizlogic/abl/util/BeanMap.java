package com.autobizlogic.abl.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A simplified BeanMap which uses our get and set methods.
 * Apache's beanutils is not sufficient for us because we want to be able
 * to get and set data members if get/set methods are not available.
 */
public class BeanMap implements Map<String, Object> {
	private Object bean;
	
	public BeanMap(Object bean) {
		this.bean = bean;
	}

	@Override
	public int size() {
		throw new RuntimeException("Not implemented for BeanMap");
	}

	@Override
	public boolean isEmpty() {
		throw new RuntimeException("Not implemented for BeanMap");
	}

	@Override
	public boolean containsKey(Object key) {
		return BeanUtil.beanHasProperty(bean, (String)key);
	}

	@Override
	public boolean containsValue(Object value) {
		throw new RuntimeException("Not implemented for BeanMap");
	}

	@Override
	public Object get(Object key) {
		if ( ! BeanUtil.beanHasProperty(bean, (String)key))
				return null;
		return BeanUtil.getBeanProperty(bean, (String)key);
	}

	@Override
	public Object put(String key, Object value) {
		Object oldValue = BeanUtil.getBeanProperty(bean, key);
		BeanUtil.setBeanProperty(bean, key, value);
		return oldValue;
	}

	@Override
	public Object remove(Object key) {
		throw new RuntimeException("Not implemented for BeanMap");
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		for (String name : m.keySet()) {
			put(name, m.get(name));
		}
	}

	@Override
	public void clear() {
		throw new RuntimeException("Not implemented for BeanMap");
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> keySet() {
		org.apache.commons.beanutils.BeanMap bmap = new org.apache.commons.beanutils.BeanMap(bean);
		return bmap.keySet();
	}

	@Override
	public Collection<Object> values() {
		throw new RuntimeException("Not implemented for BeanMap");
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		throw new RuntimeException("Not implemented for BeanMap");
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
 