package com.autobizlogic.abl.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the method is a constraint that should be executed only once, at commit time.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CommitConstraint {
	
	/**
	 * The formula can optionally be specified as an expression in the annotation,
	 * in which case the method itself will still get called, but its return value
	 * will be ignored.
	 * @return
	 */
	String value() default "";
	
	/**
	 * Specifies which attribute(s) is responsible for any failure. Multiple attributes
	 * must be comma-separated.
	 */
	String problemAttributes() default "";
	
	/**
	 * If the constraint is declarative, and it fails, this is the message that will
	 * be used in the exception.
	 * The message will be formatted with all instances of {X} being replaced by the
	 * toString value of the X attribute for the bean.
	 */
	String errorMessage() default "";
	
	/**
	 * The verb under which this constraint should be executed. By default, the constraint
	 * will be executed for all verbs. If a verb is specified, the constraint will be executed
	 * only for that verb.
	 */
	Verbs verbs() default Verbs.ALL;
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 