package com.autobizlogic.abl.rule;

/**
 * Represents the dependency of a rule on an attribute of its bean class or of 
 * a related class.
 */
public class RuleDependency {
	protected String beanClassName;
	protected String beanAttributeName;
	protected String beanRoleName;  // null for attributes of selfsame object
	
	public RuleDependency(String beanClassName, String beanAttributeName, String beanRoleName) {
		this.beanClassName = beanClassName;
		this.beanAttributeName = beanAttributeName;
		this.beanRoleName = beanRoleName;
	}
	
	/**
	 * Get the class name of the persistent
	 * @return
	 */
	public String getBeanClassName() {
		return beanClassName;
	}
	
	public String getBeanAttributeName() {
		return beanAttributeName;
	}
	
	public String getBeanRoleName() {
		return beanRoleName;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public boolean equals(Object o) {
		if ( ! (o instanceof RuleDependency))
			return false;
		RuleDependency other = (RuleDependency)o;
		if ( ! beanClassName.equals(other.beanClassName))
			return false;
		
		if ( ! beanAttributeName.equals(other.beanAttributeName))
			return false;
		
		if (beanRoleName == null && other.beanRoleName != null)
			return false;
		
		if (beanRoleName != null && other.beanRoleName == null)
			return false;
		
		if (beanRoleName != null && other.beanRoleName != null && !beanRoleName.equals(other.beanRoleName))
			return false;

		return true;
	}
	
	@Override
	public int hashCode() {
		int hashCode = beanClassName.hashCode() + beanAttributeName.hashCode();
		if (beanRoleName != null)
			hashCode += beanRoleName.hashCode();
		return hashCode;
	}
	
	@Override
	public String toString() {
		String str = beanClassName + "#" + beanAttributeName;
		if (beanRoleName != null)
			str += " - through role " + beanRoleName;
		return str;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  RuleDependency.java 98 2011-12-14 17:48:18Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 