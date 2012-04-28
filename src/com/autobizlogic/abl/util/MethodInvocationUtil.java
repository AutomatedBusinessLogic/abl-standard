package com.autobizlogic.abl.util;

import java.lang.reflect.Method;

/**
 * Utilities for invoking methods dynamically.
 */
public class MethodInvocationUtil {

	/**
	 * Invoke an arbitrary method that takes no arguments.
	 * @param target The object on which to invoke the method
	 * @param methodName The name of the method, which should take no parameters
	 * @return The value returned by the method.
	 */
	public static Object invokeMethodOnObject(Object target, String methodName) {
		try {
			// This is done this way so that we can invoke non-public methods. We don't really want to require people
			// to declare all their business logic methods as public because it's just plain distracting.
			Class<?> cls = target.getClass();
			while (cls != null) {
				Method[] allMethods = cls.getDeclaredMethods();
				for (Method meth : allMethods) {
					if (meth.getName().equals(methodName) && meth.getParameterTypes().length == 0) {
						meth.setAccessible(true);
						return meth.invoke(target, (Object[])null);
					}
				}
				cls = cls.getSuperclass();
			}
			throw new RuntimeException("No such method: " + target.getClass().getName() + "." + methodName + "()");
		}
		catch(Exception ex) {
			throw new RuntimeException("Exception while invoking method " + target.getClass().getName() + "." + methodName, ex);
		}
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
 