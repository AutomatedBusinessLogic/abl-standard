package com.autobizlogic.abl.annotations;

import java.lang.annotation.*;

/**
 * Indicates that the method is a business logic formula.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Formula {
	/**
	 * The name of the attribute whose value is computed by the formula.
	 */
	String attributeName() default "";

	/**
	 * Whether invocation of the formula should be delayed until its value
	 * is actually read.
	 */
	boolean lazy() default false;
	
	/**
	 * The formula can optionally be specified as an expression in the annotation,
	 * in which case the method itself will still get called, but its return value
	 * will be ignored.
	 * @return
	 */
	String value() default "";
	
	/**
	 * Whether the value is actually stored in the database
	 */
	boolean persistent() default true;
	
	/**
	 * Whether this formula should be skipped during recompute. This can be useful in the case
	 * of formulas used mostly to pre-populate values that can later be changed directly.
	 */
	boolean skipDuringRecompute() default false;
	
	/**
	 * Whether or not this formula should be prunable if we can determine that the data it uses
	 * has not changed. If the formula uses information other than properties of its object
	 * or its parent objects, this must be set to false.
	 * @return
	 */
	boolean pruning() default true;
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 