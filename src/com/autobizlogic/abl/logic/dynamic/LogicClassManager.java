package com.autobizlogic.abl.logic.dynamic;

/**
 * Implemented by the various classes responsible for dynamically loading logic classes.
 * <p/>
 * Implementations must have a public constructor which takes a Map<String, String> argument.
 * This Map will contain the arguments specified in ABLConfig.properties for this particular
 * logic class manager.
 */
public interface LogicClassManager {

	/**
	 * Load a logic class.
	 * @param name The name of the logic class, e.g. com.foo.businesslogic.CustomerLogic
	 * @return The logic class if found, otherwise null.
	 */
	public Class<?> getClassForName(String name);
	
	/**
	 * Get the byte code for the given logic class. This will never be called unless the
	 * given logic class has already been found using getClassForName.
	 * @param name The name of the logic class, e.g. com.foo.businesslogic.CustomerLogic
	 * @return The byte code for the logic class.
	 */
	public byte[] getByteCodeForClass(String name);
	
	/**
	 * When this is called, the implementor is expected to determine whether any of the 
	 * logic classes loaded so far have changed. If any logic class has changed, the implementor 
	 * should forget all thelogic classes it knows (as if forgetAllClasses had been called) 
	 * and return true.
	 */
	public boolean classesNeedsReloading();

	/**
	 * Get the underlying class loader. The class loader is expected to be a different object
	 * every time the classes change.
	 * @return The current ClassLoader.
	 */
	public ClassLoader getClassLoader();
	
	/**
	 * When this gets called, the LogicClassManager should forget all the logic classes it knows.
	 * It can then reload logic classes as required.
	 */
	public void forgetAllClasses();
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 