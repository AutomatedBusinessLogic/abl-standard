package com.autobizlogic.abl.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javassist.util.proxy.ProxyObject;

import com.autobizlogic.abl.metadata.MetaEntity;

/**
 * Create proxy objects for PersistentBeans. This is used to expose a PersistentBean as
 * the @OldBean value in a logic object.
 */

public class ProxyFactory {
	
	private static final Map<Class<?>, Class<?>> proxyClasses = 
			new ConcurrentHashMap<Class<?>, Class<?>>();

	/**
	 * Create a proxy object for the given PersistentBean, which must be of Pojo type.
	 * @return An instance of the Pojo class for the PersistentBean, backed by the passed
	 * PersistentBean.
	 */
	public static Object getProxyForEntity(PersistentBean bean) {
		
		MetaEntity metaEntity = bean.getMetaEntity();
		if ( ! metaEntity.isPojo())
			throw new RuntimeException("Cannot create a proxy for a non-pojo bean: " + bean);
		
		Object proxy = null;
		Class<?> beanClass = metaEntity.getEntityClass();
		Class<?> proxyClass = proxyClasses.get(beanClass);
		if (proxyClass == null) {
			javassist.util.proxy.ProxyFactory proxyFactory = new javassist.util.proxy.ProxyFactory();
			proxyFactory.setSuperclass(beanClass);
			proxyClass = proxyFactory.createClass();
			proxyClasses.put(beanClass, proxyClass);
		}
		
		try {
			proxy = proxyClass.newInstance();
		}
		catch(Exception ex) {
			throw new RuntimeException("Exception while instantiating proxy for entity " + metaEntity.getEntityName(), ex);
		}
		
		ProxyHandler proxyHandler = new ProxyHandler(bean);
		((ProxyObject)proxy).setHandler(proxyHandler);

		return proxy;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ProxyFactory.java 116 2011-12-15 02:42:41Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 