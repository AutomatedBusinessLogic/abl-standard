package com.autobizlogic.abl.engine;

/**
 * Used internally when a constraint needs to signal failure. This class should
 * never surface to the application.
 */
public class InternalConstraintException extends RuntimeException {

	private String message;
	private String[] problemAttributes;
	
	public InternalConstraintException(String msg) {
		this.message = msg;
	}
	
	public InternalConstraintException(String msg, String problemAttribute) {
		this.message = msg;
		this.problemAttributes = new String[]{ problemAttribute };
	}
	
	public InternalConstraintException(String msg, String[] problemAttributes) {
		this.message = msg;
		this.problemAttributes = problemAttributes;
	}
	
	@Override
	public String getMessage() {
		return message;
	}
	
	public String[] getProblemAttributes() {
		return problemAttributes;
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  InternalConstraintException.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 