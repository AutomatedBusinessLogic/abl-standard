package com.autobizlogic.abl.logic;

/**
 * The interface to implement if you want to control how business logic classes
 * are found.
 */
public interface BusinessLogicFinder {

	/**
	 * Get the logic class for the given bean (of type Pojo).
	 * @param beanName The full class name of the bean, e.g. com.foo.Customer
	 * @return The logic class for the bean, or null if none.
	 */
	public Class<?> getLogicClassForBeanName(String beanName);
	
	/**
	 * Get the logic class for the given bean (of type Map).
	 * @param beanName The entity name of the bean, e.g. Customer
	 * @return The logic class for the bean, or null if none.
	 */
	public Class<?> getLogicClassForEntityName(String entityName);
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 