package com.autobizlogic.abl.util;

import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;

import javassist.util.proxy.ProxyFactory;

/**
 * Utility methods to deal with class names, such as retrieving the name of the real class
 * from a (potentially) proxy object.
 */
public class ClassNameUtil {

	/**
	 * Given the full name of a class, return the name of the "real" class, i.e. the first superclass
	 * that is not a proxy class.
	 * @param rawClassName The full class name
	 * @return The full name of the first superclass that is not a proxy class
	 */
	public static String getEntityNameForClassName(String rawClassName) {
		
		Class<?> cls = ClassLoaderManager.getInstance().getClassFromName(rawClassName);
		
		while (ProxyFactory.isProxyClass(cls))
			cls = cls.getSuperclass();
		return cls.getName();
	}

	
	public static String getEntityNameForBean(Object bean) {
		Class<?> cls = bean.getClass();
		while (ProxyFactory.isProxyClass(cls))
			cls = cls.getSuperclass();
		return cls.getName();
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ClassNameUtil.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 