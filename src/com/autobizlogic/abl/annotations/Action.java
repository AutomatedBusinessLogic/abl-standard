package com.autobizlogic.abl.annotations;

import java.lang.annotation.*;

/**
 * Indicates that the method is an action.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Action {
	
	/**
	 * The verb under which this action should be executed. By default, the action
	 * will be executed for all verbs. If a verb is specified, the action will be executed
	 * only for that verb or combination of verbs.
	 * @see Verbs
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
 