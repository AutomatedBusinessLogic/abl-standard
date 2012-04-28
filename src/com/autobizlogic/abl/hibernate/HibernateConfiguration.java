package com.autobizlogic.abl.hibernate;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SettingsFactory;
import org.hibernate.event.DeleteEventListener;
import org.hibernate.event.EventListeners;
import org.hibernate.event.PostInsertEventListener;
import org.hibernate.event.PostUpdateEventListener;
import org.hibernate.impl.SessionFactoryImpl;


import com.autobizlogic.abl.VersionPrinter;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * Configure Hibernate with our event listeners and similar devices.
 */
public class HibernateConfiguration extends Configuration {

	private static Set<WeakReference<SessionFactory>> registeredSessionFactories = Collections.synchronizedSet(new HashSet<WeakReference<SessionFactory>>());

	public HibernateConfiguration() {
		super();
	}

	public HibernateConfiguration(SettingsFactory settingsFactory) {
		super(settingsFactory);
	}

	/**
	 * This is the only method intercepted by this class. It registers all the listeners before returning the
	 * session factory.
	 */
	@Override
	public SessionFactory buildSessionFactory() {
		SessionFactory sessionFactory = super.buildSessionFactory();

		registerSessionFactory(sessionFactory);

		return sessionFactory;
	}

	/**
	 * When registerSessionFactory gets called in Grails, it will need to retrieve the "real" session, which will
	 * in turn cause a recursive call to itself. We use this kludge to avoid infinite recursion.
	 */
	private static Map<Thread, Boolean> avoidInfiniteRecursion = new WeakHashMap<Thread, Boolean>();

	/**
	 * Do whatever is needed to set up a session factory to be used with ABL.
	 * This simply registers all the event listeners.
	 * @param sessionFact The session factory to be processed
	 */
	public static void registerSessionFactory(SessionFactory sessionFact) {

		if ( ! (sessionFact instanceof SessionFactoryImpl)) {

			// The only thing we support besides straight Hibernate is Grails' proxified session factory.
			if ( ! sessionFact.getClass().getName().equals("org.codehaus.groovy.grails.orm.hibernate.SessionFactoryProxy"))
				throw new RuntimeException("You cannot register a Hibernate session factory unless it is " +
						"an instance of org.hibernate.impl.SessionFactoryImpl, or org.codehaus.groovy.grails.orm.hibernate.SessionFactoryProxy");

			if (avoidInfiniteRecursion.containsKey(Thread.currentThread()))
				return;

			try {
				Class<?> cls = sessionFact.getClass();

				// First, make sure that this session factory has our class as current session context class
				Method setter = cls.getMethod("setCurrentSessionContextClass", new Class<?>[]{Class.class});
				setter.invoke(sessionFact, com.autobizlogic.abl.session.CurrentSessionContextProxy.class);

				avoidInfiniteRecursion.put(Thread.currentThread(), Boolean.TRUE);
				Method getter = cls.getMethod("getCurrentSessionFactory", (Class<?>[])null);
				Object obj = getter.invoke(sessionFact, (Object[])null);
				avoidInfiniteRecursion.remove(Thread.currentThread());

				if ( ! (obj instanceof SessionFactoryImpl))
					throw new RuntimeException("Unable to register a Grails Hibernate session factory because it is not " +
							"an instance of org.hibernate.impl.SessionFactoryImpl, but rather of " + obj.getClass().getName());
				sessionFact = (SessionFactory)obj;
			}
			catch(Exception ex) {
				ex.printStackTrace();
				throw new RuntimeException("Exception while trying to extract the SessionFactoryImpl from object of type: " + 
						sessionFact.getClass().getName(), ex);
			}
		}

		if (sessionFactoryIsRegistered(sessionFact))
			return;

		// [set] / print strategies

		LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
		if (_logger.isInfoEnabled()) {
			_logger.info("---------------------------------------------------------------------------------------------");
			_logger.info("Business Logic Configuration: ABL " + VersionPrinter.getVersion() + " - build " +
					VersionPrinter.getBuildNumber() + " (" + VersionPrinter.getBuildDate() + ")");
			try {
				File jarLocation = new File
						(HibernateConfiguration.class.getProtectionDomain()
								.getCodeSource().getLocation().toURI());
				_logger.info("| Loaded from: " + jarLocation);
			} catch (Exception ex) {
				_logger.info("| Loaded from unknown location");
			}
		}

		SessionFactoryImpl sfi = (SessionFactoryImpl)sessionFact;
		EventListeners listeners = sfi.getEventListeners();

		synchronized(listeners) {
			// We record the fact that an object is deleted so that, during rules processing, we can tell if e.g. a parent
			// object was deleted.
			addListener(listeners, new LogicEventListener(), DeleteEventListener.class, "DeleteEventListeners", false);

			addListener(listeners, new LogicEventListener(), PostInsertEventListener.class, "PostInsertEventListeners", false);
			addListener(listeners, new LogicEventListener(), PostUpdateEventListener.class, "PostUpdateEventListeners", false);
		}

		if (_logger.isInfoEnabled()) {
			_logger.info("Business Logic Configuration complete");
			_logger.info("---------------------------------------------------------------------------------------------");
		}

		// Clean up the collection of session factories, and add the new one
		Set<WeakReference<SessionFactory>> toRemove = new HashSet<WeakReference<SessionFactory>>();
		for (WeakReference<SessionFactory> ref : registeredSessionFactories) {
			if (ref.get() == null)
				toRemove.add(ref);
		}
		registeredSessionFactories.removeAll(toRemove);
		registeredSessionFactories.add(new WeakReference<SessionFactory>(sessionFact));

		// EXPERIMENTAL
		//ConsoleServer.startService();
	}

	/**
	 * Get all the known session factories
	 */
	public static Set<SessionFactory> getRegisteredSessionFactories() {
		Set<SessionFactory> result = new HashSet<SessionFactory>();

		synchronized(registeredSessionFactories) {
			for (WeakReference<SessionFactory> ref : registeredSessionFactories) {
				if (ref.get() != null && !ref.get().isClosed())
					result.add(ref.get());
			}
		}

		return Collections.unmodifiableSet(result);
	}

	private static boolean sessionFactoryIsRegistered(SessionFactory fact) {
		synchronized(registeredSessionFactories) {
			for (WeakReference<SessionFactory> ref : registeredSessionFactories) {
				if (ref.get() != null && !ref.get().isClosed() && ref.get().equals(fact))
					return true;
			}
		}
		return false;
	}

	public static SessionFactory getSessionFactoryById(String id) {
		for (WeakReference<SessionFactory> ref : registeredSessionFactories)
			if (ref.get() != null && !ref.get().isClosed() && ("" + ref.get().hashCode()).equals(id))
				return ref.get();

		return null;
	}

	/**
	 * Add the given listener to the given listener list
	 * @param listeners The EventListeners object for a Configuration
	 * @param newListener The new listener to add
	 * @param listenerClass The class of the listener interface, e.g. PostInsertEventListener.class
	 * @param listenerName The name of the listener, e.g. "PostInsertEventListener"
	 */
	public static void addListener(EventListeners listeners, Object newListener, Class<?> listenerClass, String listenerName, boolean registerFirst) {
		try {
			LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);

			Class<?> listenersCls = listeners.getClass();
			Method getMethod = listenersCls.getMethod("get" + listenerName);
			Object[] oldListeners = (Object[])getMethod.invoke(listeners);
			int numListeners = 0;
			if (oldListeners != null)
				numListeners = oldListeners.length;

			// Check if our listener is already in place
			if (oldListeners != null) {
				for (Object l : oldListeners) {
					if (l instanceof LogicEventListener) {
						_logger.debug("Session factory already has ABL listener " + listenerName + " -- skipping.");
						return;
					}
				}
			}

			// Create a new array of size n+1 and add the new listener to it
			Object[] newListeners;
			if (registerFirst) { // The listener goes first in the list
				newListeners = new Object[numListeners + 1];
				newListeners = (Object[])java.lang.reflect.Array.newInstance(listenerClass, numListeners + 1);
				newListeners[0] = newListener;
				if (oldListeners != null)
					for (int i = 0; i < numListeners; i++)
						newListeners[i + 1] = oldListeners[i];
			}
			else { // The listeners goes last in the list
				if (oldListeners != null)
					newListeners = Arrays.copyOf(oldListeners, numListeners + 1);
				else
					newListeners = (Object[])Array.newInstance(listenerClass, numListeners + 1);
				newListeners[numListeners] = newListener;
			}

			// Finally set it in the EventListeners
			Method setMethod = listenersCls.getMethod("set" + listenerName, newListeners.getClass());
			setMethod.invoke(listeners, new Object[]{newListeners});
		}
		catch(Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException("Unable to add listener " + listenerName, ex);
		}
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibernateConfiguration.java 1109 2012-04-08 21:20:07Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 