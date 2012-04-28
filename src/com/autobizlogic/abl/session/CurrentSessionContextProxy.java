package com.autobizlogic.abl.session;

import java.lang.reflect.Constructor;

import org.hibernate.HibernateException;
import org.hibernate.classic.Session;
import org.hibernate.context.CurrentSessionContext;
import org.hibernate.engine.SessionFactoryImplementor;

import com.autobizlogic.abl.config.ConfigurationException;
import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;
import com.autobizlogic.abl.hibernate.HibernateConfiguration;
import com.autobizlogic.abl.text.LogicMessageFormatter;
import com.autobizlogic.abl.text.MessageName;

/**
 * A proxy for a CurrentSessionContext, which makes sure that our listeners are registered.
 */
public class CurrentSessionContextProxy implements CurrentSessionContext {
	
	private CurrentSessionContext context;

	public CurrentSessionContextProxy(SessionFactoryImplementor factory) {
		
		// If this is not an instance of a subclass, we are responsible for setting up
		// the transaction factory from the configuration.
		if ( ! this.getClass().getName().equals(CurrentSessionContextProxy.class.getName()))
			return;
		
		// Since we are not a subclass, we need to look up the class name for the transaction factory,
		// and instantiate it.
		String ctxtClassName = LogicConfiguration.getInstance().getProperty(PropertyName.CURRENT_SESSION_CONTEXT_CLASS);
		if (ctxtClassName == null)
			throw new ConfigurationException(LogicMessageFormatter.getMessage(MessageName.session_NoSessionContextClass, 
					new Object[]{PropertyName.CURRENT_SESSION_CONTEXT_CLASS.getName()}));
		
		try {
			Class<?> ctxtClass = Class.forName(ctxtClassName);
			Constructor<?> constructor = ctxtClass.getConstructor(new Class[]{SessionFactoryImplementor.class});
			context = (CurrentSessionContext)constructor.newInstance(factory);
		} catch (Exception ex) {
			throw new ConfigurationException(LogicMessageFormatter.getMessage(MessageName.session_ContextCreationException, 
					new Object[]{ctxtClassName}), ex);
		}

		// Now register our event listeners
		HibernateConfiguration.registerSessionFactory(factory);
	}
	
	@Override
	public Session currentSession() throws HibernateException {
		Session sess = context.currentSession();
		return sess;
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  CurrentSessionContextProxy.java 109 2011-12-14 23:31:43Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 