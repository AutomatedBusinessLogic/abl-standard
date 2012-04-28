package com.autobizlogic.abl.engine;

import java.io.Serializable;

/**
 * Represents the failure of a constraint. These can be retrieved from a ConstraintException.
 */

public class ConstraintFailure {
	private String constraintMessage;
	private String[] problemAttributes;
	private String problemClass;
	private Serializable problemPk;
	private String constraintName;
	private String logicClassName;
	
	public ConstraintFailure(String aMsg, String[] aProblemAttributeArray) {
		constraintMessage = aMsg;
		problemAttributes = aProblemAttributeArray;
	}
	
	/**
	 * Get the message that describes this constraint failure.
	 */
	public String getConstraintMessage() {
		return constraintMessage;
	}

	public void setConstraintMessage(String constraintMessage) {
		this.constraintMessage = constraintMessage;
	}

	/**
	 * Get the names of the attributes associated with this constraint failure.
	 * @return Null if there are no attributes associated with this constraint failure,
	 * otherwise one or more names of attributes for the object that caused the failure.
	 */
	public String[] getProblemAttributes() {
		return problemAttributes;
	}

	public void setProblemAttributes(String[] problemAttributes) {
		this.problemAttributes = problemAttributes;
	}
	
	/**
	 * Get the name of the persistent class that caused this constraint failure.
	 */
	public String getProblemClass() {
		return problemClass;
	}
	
	public void setProblemClass(String clsName) {
		problemClass = clsName;
	}
	
	/**
	 * Get the primary key of the object that caused this constraint failure.
	 */
	public Serializable getProblemPk() {
		return problemPk;
	}
	
	public void setProblemPk(Serializable pk) {
		problemPk = pk;
	}
	
	/**
	 * Get the name of the logic class that contains the constraint.
	 */
	public String getLogicClassName() {
		return logicClassName;
	}
	
	public void setLogicClassName(String s) {
		logicClassName = s;
	}
	
	/**
	 * Get the name of the constraint method.
	 */
	public String getConstraintName() {
		return constraintName;
	}
	
	public void setConstraintName(String s) {
		constraintName = s;
	}

	/**
	 * Signal that a constraint has failed, in other words, the condition that is enforced
	 * by the constraint is not true.
	 * This method can be statically imported into business rule classes for easier use.
	 * @param msg A message explaining why the constraint is not satisfied.
	 */
	public static void failConstraint(String msg) {
		throw new InternalConstraintException(msg);
	}
	
	/**
	 * Signal that a constraint has failed, in other words, the condition that is enforced
	 * by the constraint is not true.
	 * This method can be statically imported into business rule classes for easier use.
	 * @param msg A message explaining why the constraint is not satisfied.
	 * @param problemAttribute Optional: the name of the attribute causing the problem.
	 */
	public static void failConstraint(String msg, String problemAttribute) {
		throw new InternalConstraintException(msg, problemAttribute);
	}
	
	/**
	 * Signal that a constraint has failed, in other words, the condition that is enforced
	 * by the constraint is not true.
	 * This method can be statically imported into business rule classes for easier use.
	 * @param msg A message explaining why the constraint is not satisfied.
	 * @param problemAttributes Optional: the names of the attributes causing the problem.
	 */
	public static void failConstraint(String msg, String[] problemAttributes) {
		throw new InternalConstraintException(msg, problemAttributes);
	}
	
	@Override
	public String toString() {
		return "Constraint failure : " + getConstraintMessage();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ConstraintFailure.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 