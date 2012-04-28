package com.autobizlogic.abl.util;

import java.util.Collection;

/**
 * Various utility methods regarding strings.
 */
public class StringUtil {

	/**
	 * If the given string has double quotes around it, remove them and return the result
	 * @param str The string in question. Can be null, in which case null will be returned.
	 * @return The string minus any leading or trailing double quote
	 */
	static public String stripSurroundingDoubleQuotes(String str) {
		if (str == null || str.length() == 0)
			return str;

		if (str.charAt(0) == '"')
			str = str.substring(1);

		if (str.charAt(str.length() - 1) == '"')
			str = str.substring(0, str.length() - 1);

		return str;
	}
	
	/**
	 * Take a collection of anything and return it formatted as a string.
	 * @param coll A collection of anything
	 * @return [ <object toString>, ... ]
	 */
	static public String collectionToString(@SuppressWarnings("rawtypes") Collection coll) {
		if (coll == null)
			return "";
		StringBuffer sb = new StringBuffer();
		
		sb.append("[");
		for (Object o : coll) {
			sb.append(o.toString());
			sb.append(",");
		}
		sb.append("]");
		
		return sb.toString();
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  StringUtil.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 