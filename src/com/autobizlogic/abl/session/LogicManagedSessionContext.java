package com.autobizlogic.abl.session;

import org.hibernate.HibernateException;
import org.hibernate.classic.Session;
import org.hibernate.context.ManagedSessionContext;
import org.hibernate.engine.SessionFactoryImplementor;

import com.autobizlogic.abl.hibernate.HibernateConfiguration;

/**
 * The ABL drop-in replacement for Hibernate's ManagedSessionContext.
 * This class is specified in the Hibernate configuration as the value for
 * hibernate.current_session_context_class.
 */
public class LogicManagedSessionContext extends ManagedSessionContext {

	public LogicManagedSessionContext(SessionFactoryImplementor factory) {
		super(factory);

		// Now register our event listeners
		HibernateConfiguration.registerSessionFactory(factory);
	}
	
	/**
	 * We override currentSession so that, if something goes wrong, the exception will
	 * come from us, rather than ManagedSessionContext, which tells us that our class
	 * is correctly installed.
	 */
	@Override
	public Session currentSession() {
		try {
			Session session = super.currentSession();
			return session;
		}
		catch(HibernateException ex) {
			throw new RuntimeException("Unable to retrieve current session", ex);
		}
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicManagedSessionContext.java 649 2012-02-01 06:04:19Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 