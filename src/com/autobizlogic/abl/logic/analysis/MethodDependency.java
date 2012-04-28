package com.autobizlogic.abl.logic.analysis;

/**
 * Represents a dependency on a method in a class.
 */
public class MethodDependency {

	private ClassDependency classDepend;
	
	/**
	 * The name of the method on which we depend
	 */
	private String methodName;
	
	protected MethodDependency(ClassDependency classDepend, String methodName) {
		this.classDepend = classDepend;
		this.methodName = methodName;
	}
	
	public ClassDependency getClassDependency() {
		return classDepend;
	}
	
	public String getMethodName() {
		return methodName;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////
	// Menial stuff
	
	@Override
	public String toString() {
		String s = "Dependency on method " + classDepend.getClassName() + "." + methodName;
		return s;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  MethodDependency.java 121 2011-12-15 19:59:31Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 