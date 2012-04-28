package com.autobizlogic.abl.logic.analysis;

/**
 * Represents a dependency upon a specific property of a specific class.
 */
public class PropertyDependency {

	private ClassDependency classDepend;
	
	/**
	 * The name of the property on which we depend
	 */
	private String propertyName;
	
	/**
	 * If not null, this is the role used to access the object before we call its method.
	 */
	private String roleName;
	
	protected PropertyDependency(ClassDependency classDepend, String propertyName, String roleName) {
		this.classDepend = classDepend;
		this.propertyName = propertyName;
		this.roleName = roleName;
	}
	
	public ClassDependency getClassDependency() {
		return classDepend;
	}
	
	public String getPropertyName() {
		return propertyName;
	}
	
	public String getRoleName() {
		return roleName;
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////
	// Menial stuff
	
	@Override
	public String toString() {
		String s = "Dependency on property " + classDepend.getClassName() + "." + propertyName;
		if (roleName != null)
			s += " through role " + roleName;
		return s;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  PropertyDependency.java 121 2011-12-15 19:59:31Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 