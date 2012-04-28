package com.autobizlogic.abl.annotations;

import java.lang.annotation.*;

/**
 * Indicates that the method is a business logic count.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Count {
	/*
	 * The expression in the format "rolename [where condition]", e.g.
	 * <ol>
	 * <li> orders
	 * <li> orders where paid = false
	 * </ol>
	 */
	String value();
	
	/**
	 * The name of the bean attribute whose value this rule derives.
	 * <br>
	 * 
	 * Optional - if omitted, the method name must be of the form
	 * derive&lt;attributeName>
	 */
	String attributeName() default "";

	/**
	 * Whether the initialization of the sum (for non-persistent attributes)
	 * should be done in memory (as opposed to the standard SQL query).
	 */
	boolean inMemory() default false;
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 