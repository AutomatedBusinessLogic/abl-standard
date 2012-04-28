package com.autobizlogic.abl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the attribute should be copied from the specified parent attribute
 * when the object is assigned to that parent.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ParentCopy {

	/**
	 * The specification of the parent attribute, in the form <role-name>.<attribute-name>
	 */
	String value();

	/**
	 * The name of the attribute whose value is copied from the parent attribute (optional, derived
	 * from method name if not specified).
	 */
	String attributeName() default "";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 