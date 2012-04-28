package com.autobizlogic.abl.annotations;

import java.lang.annotation.*;

/**
 * Indicates that the method is a business logic sum.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Sum {
	/**
	 * The name of the bean attribute whose value this rule derives.
	 * <br>
	 * 
	 * Optional - if omitted, the method name must be of the form
	 * derive&lt;attributeName>
	 */
	String attributeName() default "";
	
   /**
	 * The expression in the format "childrenrolename.attributename [where condition]", e.g.
	 * <ol>
	 * <li> purchaseorders.amountUnPaid
	 * <li> purchaseorders.amountUnPaid where isReady = true
	 * </ol>
	 * 
	 * Optional - if omitted, no other arguments are allowed,<br>
	 * and specify this as the (un-named) argument.
	 */
	String value();

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
 