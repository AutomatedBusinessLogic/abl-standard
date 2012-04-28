package com.autobizlogic.abl.annotations;

import java.lang.annotation.ElementType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Derives the minimum value for a given attribute among child objects that satisfy an optional clause.
 * If no child qualifies, or if the value(s) is null, the parent attribute will be set to null.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Minimum {
	/**
	 * The name of the bean attribute whose value this rule derives.
	 * <br>
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
	 * Optional - if omitted, no other arguments are allowed,
	 * and specify this as the (un-named) argument.
	 */
	String value();
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 