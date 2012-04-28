package com.autobizlogic.abl.engine;

import java.util.List;

import org.hibernate.HibernateException;


/**
 * Constraint failures throw these, a subclass of HibernateException.
 * It is a subclass of HibernateException because RuntimeExceptions are translated 
 * in ActionQueue (see code for ActionQueue), and we don't want that -- we want
 * our exception to surface.
 */
public class ConstraintException extends HibernateException {
	
	private static final long serialVersionUID = 1L;
	List<ConstraintFailure> constraintFailures;

	public ConstraintException(String aMsg, List<ConstraintFailure> aFailureList) {
		super(aMsg);
		constraintFailures = aFailureList;
	}
	
	public List<ConstraintFailure> getConstraintFailures() {
		return constraintFailures;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ConstraintException.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 