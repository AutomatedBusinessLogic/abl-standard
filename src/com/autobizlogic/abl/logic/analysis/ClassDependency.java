package com.autobizlogic.abl.logic.analysis;

import java.util.*;

/**
 * Stand-in for a class in dependency graphs. There should only be one instance of this class
 * per Java class; this is ensured by LogicAnalysisManager.
 */
public class ClassDependency {

	protected String className;
	
	protected Map<String, PropertyDependency> propertyDependencies = new HashMap<String, PropertyDependency>();
	
	protected Map<String, MethodDependency> methodDependencies = new HashMap<String, MethodDependency>();
	
	protected ClassDependency(String className) {
		this.className = className;
	}
	
	/**
	 * Get the name of the class to which is the dependency.
	 */
	public String getClassName() {
		return className;
	}
	
	/**
	 * Get the dependency for the given property through the given role (if applicable).
	 * @param propName The name of the porperty
	 * @param roleName The name of the role (can be null)
	 * @return The dependency, or null if not found.
	 */
	public PropertyDependency getPropertyDependency(String propName, String roleName) {
		String fullName = propName;
		if (roleName != null)
			fullName += "/" + roleName;
		return propertyDependencies.get(fullName);
	}
	
	/**
	 * Retrieve or create a PropertyDependency for a given method of the class represented by this object.
	 * This ensures that the same dependency is not represented more than once.
	 * @param propName The name of the property
	 * @param roleName The name of the role (if any)
	 */
	public PropertyDependency getOrCreatePropertyDependency(String propName, String roleName) {
		String fullName = propName;
		if (roleName != null)
			fullName += "/" + roleName;
		synchronized(propertyDependencies) {
			PropertyDependency result = propertyDependencies.get(fullName);
			if (result == null)
			{
				result = new PropertyDependency(this, propName, roleName);
				propertyDependencies.put(fullName, result);
			}
			return result;
		}
	}
	
	/**
	 * Get all the property dependencies for this class.
	 */
	public Collection<PropertyDependency> getPropertyDependencies() {
		return propertyDependencies.values();
	}
	
	/**
	 * Retrieve or create a MethodDependency for a given method of the class represented by this object.
	 * This ensures that the same dependency is not represented more than once.
	 * @param methodName The name of the method
	 */
	public MethodDependency getOrCreateMethodDependency(String methodName) {
		synchronized(methodDependencies) {
			MethodDependency result = methodDependencies.get(methodName);
			if (result == null)
			{
				result = new MethodDependency(this, methodName);
				methodDependencies.put(methodName, result);
			}
			return result;
		}
	}
	
	/**
	 * Get all the property dependencies for this class.
	 */
	public Collection<MethodDependency> getMethodDependencies() {
		return methodDependencies.values();
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o == this)
			return true;
		if ( ! (o instanceof ClassDependency))
			return false;
		ClassDependency dep = (ClassDependency)o;
		return getClassName().equals(dep.getClassName());
	}
	
	@Override
	public int hashCode() {
		return getClassName().hashCode();
	}
		
	@Override
	public String toString() {
		return "Dependency on class " + className;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ClassDependency.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 