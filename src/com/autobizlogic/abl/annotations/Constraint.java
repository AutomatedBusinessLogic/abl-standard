package com.autobizlogic.abl.annotations;

import java.lang.annotation.*;

/**
 * Indicates that the method is a business logic constraint.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Constraint {

	/**
	 * The constraint can optionally be specified as an expression in the annotation,
	 * in which case the method itself will still get called, but any exception it throws
	 * will be ignored.
	 * <p/>
	 * The expression has direct access to the object's attributes and relationships,
	 * and should evaluate to true if the constraint is to pass, and false if the
	 * constraint is to fail.
	 * <p/>
	 * The syntax available is provided by <a href="http://commons.apache.org/jexl/reference/syntax.html">Jexl</a>.
	 * All of Jexl's amenities are therefore available to the user.
	 * <p/>
	 * A few examples:
	 * <p/>
	 * <code>balance <= creditLimit</code>
	 * <br/>
	 * <code>(balance * taxRate) + fixedFee < maxOrderPrice</code>
	 * <br/>
	 * <code>code =~ 'X.*'</code>
	 * <p/>
	 * Generally speaking, specifying a constraint as an expression is fine, but it is
	 * much slower than specifying it as Java code, because it is interpreted at runtime.
	 * It should therefore only be used when performance is not critical.
	 * <p/>
	 * In addition, even though Jexl provides advanced facilities, anything beyond a simple
	 * expression should almost always be done as code, to make debugging easier.
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
	 * The message will be formatted with all instances of {xxx} being replaced by the
	 * toString value of the xxx attribute for the bean.
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
 