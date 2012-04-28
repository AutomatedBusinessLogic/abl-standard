package com.autobizlogic.abl.util;

import javassist.util.proxy.ProxyFactory;

/**
 * Utility methods dealing with proxy objects.
 */
public class ProxyUtil {

	/**
	 * Given an object, return the "real" class for it, namely the first
	 * superclass that is not a Javassist proxy class.
	 */
	public static Class<?> getNonProxyClass(Object bean) {
		Class<?> beanClass = bean.getClass();
		while (ProxyFactory.isProxyClass(beanClass)) {
			beanClass = beanClass.getSuperclass();
		}
		return beanClass;
	}
	
	/**
	 * Reach into the guts of a proxy object and get the real bean.
	 */
	public static Object getNonProxyObject(Object bean) {
		if ( ! ProxyFactory.isProxyClass(bean.getClass()))
			return bean;
		bean.hashCode(); // Seems absurd, doesn't it? But if bean is an uninitialized proxy, this will fix it.
		Object handler = ObjectUtil.getFieldValue(bean, "handler");
		Object realBean = ObjectUtil.getFieldValue(handler, "target");
		if (realBean == null)
			throw new RuntimeException("Unable to retrieve real bean from proxy: " + bean);
		return realBean;
	}
	
	////////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ProxyUtil.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 