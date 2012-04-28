package com.autobizlogic.abl.session;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.ActionQueue;
import org.hibernate.event.AbstractEvent;
import org.hibernate.impl.SessionImpl;

import com.autobizlogic.abl.hibernate.AfterTransactionProcess;
import com.autobizlogic.abl.hibernate.BeforeTransactionProcess;
import com.autobizlogic.abl.hibernate.HibernateSessionUtil;
import com.autobizlogic.abl.util.LogicLogger;

/**
 * Utility class to easily retrieve the current LogicTransaction and associated objects.
 */

public class LogicTransactionManager {

	/**
	 * Keep track of which sessions have already had our LogicEventListener
	 * registered. We use a WeakHashMap so as not to prevent the GC'ing of the
	 * session.
	 */
	private static Map<Session, Boolean> initializedSessions = new WeakHashMap<Session, Boolean>();

	/**
	 * Keep track of all the LogicTransactionContexts.
	 */
	private static Map<Transaction, LogicTransactionContext> txContexts = new WeakHashMap<Transaction, LogicTransactionContext>();

	private final static LogicLogger log = LogicLogger
			.getLogger(LogicLogger.LoggerName.PERSISTENCE);

	/**
	 * Get the current LogicTransactionContext, given an event.
	 */
	public static LogicTransactionContext getCurrentLogicTransactionContext(AbstractEvent event) {
		Transaction tx = event.getSession().getTransaction();
		if (tx == null)
			return null;

		return getCurrentLogicTransactionContextForTransaction(tx, event.getSession());
	}

	/**
	 * Get the LogicTransactionContext for this transaction. The session is
	 * passed in in case we need to create the LogicTransactionContext (i.e.
	 * this transaction has not yet been seen).
	 */
	public static LogicTransactionContext getCurrentLogicTransactionContextForTransaction(
			Transaction tx, Session session) {
		LogicTransactionContext ctxt;
		synchronized(txContexts) {
			ctxt = txContexts.get(tx);
		}
		if (ctxt != null) {
			return ctxt;
		}

		// First time for this transaction -- create a new context
		ctxt = new LogicTransactionContext();
		
//		if (Proxy.isProxyClass(session.getClass()))
//			throw new RuntimeException("You must not pass a proxified session, as this will cause much misery later on.");
		
		// I added this while chasing a nasty bug. This is really ugly, but I see no way around it if
		// we want to allow this method to be called (typically indirectly, e.g. via LogicContext.setCurrentUseCaseName)
		// from user code.

		if (Proxy.isProxyClass(session.getClass())) {
			InvocationHandler handler = Proxy.getInvocationHandler(session);
			// Ugh! This is really horrible. We blindly assume that the proxy is a ThreadLocalSessionContext.TransactionProtectionWrapper.
			try {
				Field realSessionField = handler.getClass().getDeclaredField("realSession");
				realSessionField.setAccessible(true);
				session = (Session)realSessionField.get(handler);
			}
			catch(Exception ex) {
				throw new RuntimeException("Error while unpacking real session from proxy", ex);
			}
		}

		ctxt.setSession(session);
		synchronized(txContexts) {
			txContexts.put(tx, ctxt);
		}

		// Also, if we have not yet seen this session, register our
		// BeforeTransaction listener
		//if (!initializedSessions.containsKey(session)) {
		SessionImpl sessImpl = (SessionImpl)session;
		if ( ! actionQueueIsRegistered(sessImpl)) {
			if (session.getTransaction() == null) {
				log.fatal("Session does not have an open transaction");
				throw new RuntimeException("Session does not have an open transaction");
			}
			// The following does not work for JPA
			//if ( ! session.getTransaction().isActive()) {
			//	log.fatal("Session does not have an open transaction");
			//	throw new RuntimeException("Session does not have an open transaction");
			//}
			if ( ! session.isOpen()) {
				log.fatal("Session is not open");
				throw new RuntimeException("Session is not open");
			}
			ActionQueue aq = HibernateSessionUtil
					.getActionQueueForSession(session);
			aq.registerProcess(new BeforeTransactionProcess());
			aq.registerProcess(new AfterTransactionProcess());
			synchronized(initializedSessions) {
				initializedSessions.put(session, Boolean.TRUE);
				
				// Take advantage of this opportunity to remove any closed sessions
				Set<Session> closedSessions = new HashSet<Session>();
				for (Session sess : initializedSessions.keySet()) {
					if (!sess.isOpen())
						closedSessions.add(sess);
				}
				for (Session sess : closedSessions)
					initializedSessions.remove(sess);
			}
		}

		return ctxt;
	}

	/**
	 * Experimental: try to peek in the session to see if our action queue processes are there.
	 */
	private static boolean actionQueueIsRegistered(SessionImpl session) {
		ActionQueue aq = HibernateSessionUtil.getActionQueueForSession(session);
		try {
			Field fld1 = aq.getClass().getDeclaredField("beforeTransactionProcesses");
			fld1.setAccessible(true);
			Object beforeProcesses = fld1.get(aq);
			Field fld2 = beforeProcesses.getClass().getDeclaredField("processes");
			fld2.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<Object> processes = (List<Object>)fld2.get(beforeProcesses);
			for (Object o : processes) {
				if (o instanceof BeforeTransactionProcess)
					return true;
			}
		}
		catch(Exception ex) {
			throw new RuntimeException("Exception while checking the SessionImpl beforeTransactionProcesses " +
					"to see if our process was already registered.", ex);
		}
		
		return false;
	}

	/**
	 * This gets called by the AfterTransactionProcess that was registered in
	 * getCurrentLogicTransactionContextForTransaction, so that we can clean up
	 * the lookup table. There are cases when the same transaction object gets
	 * reused, which can cause us to confuse them.
	 * 
	 * @param tx
	 */
	public static void transactionHasCompleted(Session session) {
		Transaction finishedTx = null;
		synchronized(txContexts) {
			for (Transaction tx : txContexts.keySet()) {
				LogicTransactionContext ctxt = txContexts.get(tx);
				if (ctxt == null)
					continue;
				if (ctxt.getSession().equals(session)) {
					finishedTx = tx;
					break;
				}
			}
		}
		if (finishedTx == null)
			log.warn("Tried to unregister an unknown transaction");
		else {
			synchronized(txContexts) {
				txContexts.remove(finishedTx);
			}
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicTransactionManager.java 813 2012-02-24 07:21:21Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 