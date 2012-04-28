package com.autobizlogic.abl.util;

public class BeanNameUtil {

	/**
	 * Given the name of a getter method such as getName or isPaid, return the name
	 * of the property. If the method name does not start with get or is, return null;
	 */
	public static String getPropNameFromGetMethodName(String methodName) {
		if ( ! (methodName.startsWith("get") || methodName.startsWith("is")))
			return null;
		if (methodName.startsWith("get") && methodName.length() == 3)
			return null;
		if (methodName.startsWith("is") && methodName.length() == 2)
			return null;
		String propName = null;
		if (methodName.startsWith("get")) {
			propName = methodName.substring(3, 4).toLowerCase();
			if (methodName.length() > 4)
				propName += methodName.substring(4);
		}
		else {
			propName = methodName.substring(2, 3).toLowerCase();
			if (methodName.length() > 3)
				propName += methodName.substring(3);
		}
		return propName;
	}
	
	/**
	 * Given the name of a setter method such as setName, return the name of the
	 * property (name in this case). If the method name does not start with "set",
	 * or is just "set", return null.
	 */
	public static String getPropNameFromSetMethodName(String methodName) {
		if ( ! methodName.startsWith("set"))
			return null;
		if (methodName.length() == 3)
			return null;
		
		String propName = methodName.substring(3, 4).toLowerCase();
		if (methodName.length() > 4)
			propName += methodName.substring(4);

		return propName;
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  BeanNameUtil.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 