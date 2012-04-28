package com.autobizlogic.abl.hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.hibernate.Session;
import org.hibernate.engine.ActionQueue;
import org.hibernate.engine.PersistenceContext;
import org.hibernate.impl.SessionImpl;
import org.hibernate.persister.entity.EntityPersister;

/**
 * In a JavaEE environment, Hibernate may often wrap the session in a dynamic proxy (Proxy), in which case
 * we have to ask nicely to retrieve certain internals objects from the underlying SessionImpl.
 */
public class HibernateSessionUtil {

	public static ActionQueue getActionQueueForSession(Session session) {
		if (Proxy.isProxyClass(session.getClass()))
			return (ActionQueue)invokeMethod(session, "getActionQueue");
		
		return ((SessionImpl)session).getActionQueue();
	}
	
	public static PersistenceContext getPersistenceContextForSession(Session session) {
		if (Proxy.isProxyClass(session.getClass()))
			return (PersistenceContext)invokeMethod(session, "getPersistenceContext");
		
		return ((SessionImpl)session).getPersistenceContext();
	}
	
	public static EntityPersister getEntityPersister(Session session, String entityName, Object bean) {
		if (Proxy.isProxyClass(session.getClass())) {
			try {
				Method method = session.getClass().getMethod("getEntityPersister", new Class[]{String.class, Object.class});
				return (EntityPersister)method.invoke(session, new Object[]{entityName, bean});
			}
			catch(Exception ex) {
				throw new RuntimeException("Exception while invoking getEntityPersister on session", ex);
			}
		}
		
		return ((SessionImpl)session).getEntityPersister(entityName, bean);
	}
	
	/**
	 * Given a Hibernate Session, retrieve the "real" session if it's a proxy.
	 */
	public static Session getRealSession(Session session) {
		Session realSession = session;
		if (Proxy.isProxyClass(session.getClass())) {
			InvocationHandler handler = Proxy.getInvocationHandler(session);
			try {
				Field fld = handler.getClass().getDeclaredField("realSession");
				fld.setAccessible(true);
				realSession = (Session)fld.get(handler);
			}
			catch(Exception ex) {
				throw new RuntimeException("Error while getting the real Hibernate session out of proxy session", ex);
			}
		}
		return realSession;
	}
	
	private static Object invokeMethod(Session session, String methodName) {
		try {
			Method method = session.getClass().getMethod(methodName, (Class[])null);
			return method.invoke(session, (Object[])null);
		}
		catch(Exception ex) {
			throw new RuntimeException("Exception while invoking " + methodName + " on session", ex);
		}
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 