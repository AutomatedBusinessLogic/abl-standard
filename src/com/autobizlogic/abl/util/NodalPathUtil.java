package com.autobizlogic.abl.util;

/**
 * Utility methods to deal with nodal paths, ie names separated by periods such as
 * com.foo.blah or yabba.dabba.Doo
 */
public class NodalPathUtil {

	/**
	 * Given a nodal path of the form com.foo.bar.something, return everything but the last part
	 * e.g. com.foo.bar
	 * @param nodalPath The period-separated path
	 * @return The path minus the last part, or an empty string if there were no periods in the string.
	 */
	public static String getNodalPathPrefix(String aNodalPath) {
		int lastIdx = aNodalPath.lastIndexOf('.');
		if (lastIdx == -1)
			return "";
		
		return aNodalPath.substring(0, lastIdx);
	}


	/**
	 * Given a nodal path of the form com.foo.bar.Wazoo, return the last part (e.g. Wazoo in this case).
	 * @param aNodalPath The period-separated path
	 * @return The last part of the path. If the path contains no period, then the path is returned.
	 */
	public static String getNodalPathLastName (String aNodalPath) {
		int lastIdx = aNodalPath.lastIndexOf('.');
		if (lastIdx == -1)
			return aNodalPath;
		
		return aNodalPath.substring(lastIdx + 1);
	}

	/**
	 * 
	 * @param aNodalName
	 * @return getNodalPathLastName, with first character in lower case
	 */
	public static String getNodalPathLastNameAsVariable(String aNodalName) {
		String nodalPathLastName = getNodalPathLastName(aNodalName);
		String nodalPathLastNameFirstChar = nodalPathLastName.substring (0,1).toLowerCase();
		return nodalPathLastNameFirstChar + nodalPathLastName.substring(1);
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  NodalPathUtil.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 