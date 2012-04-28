package com.autobizlogic.abl.logic;

/**
 * Indicates what initiated a LogicRunner.
 */
public enum LogicSource {
	/**
	 * Indicates a LogicRunner that was created to handle an object deletion caused by
	 * a cascade.
	 */
	CASCADE_DELETED,
	
	/**
	 * Indicates a LogicRunner that was created to handle an object being adjusted by
	 * some sort of foward chaining.
	 */
	ADJUSTED,
	CASCADED,
	LOGIC,		// issued from Business Logic Component
	
	/**
	 * Indicates that the logic runner was created in response to an action by the client code.
	 * This is the "prime mover" type of LogicRunner. All other values mean that the logic runner 
	 * was created indirectly from one of these.
	 */
	USER
}




/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 