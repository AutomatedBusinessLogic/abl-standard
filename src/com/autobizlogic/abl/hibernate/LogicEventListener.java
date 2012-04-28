package com.autobizlogic.abl.hibernate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.event.*;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.logic.BusinessLogicFactoryManager;
import com.autobizlogic.abl.logic.LogicSource;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.logic.BusinessLogicFactory;
import com.autobizlogic.abl.event.ObjectEvent;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.session.LogicTransactionManager;
import com.autobizlogic.abl.util.ClassNameUtil;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;


/**
 * Event Handlers (configured in HibernateConfiguration) respond to Hibernate events to invoke LogicRunners,<br>
 * which order, optimize and invoke the logic in user-specified Logic Components.
 * <p>
 * 
 * All sorts of issues arise issuing updates and accessing data on preEvents and onSave.  
 * We therefore employ the following Queued Events strategy 
 * {@see <a href="http://opensource.atlassian.com/projects/hibernate/secure/attachment/15486/HibernateWorkaroundHH2763.java">HibernateWorkaroundHH2763</a> }:
 * 
 * <h5>Submit Phase</h5>
 * <code>postEvents</code> create/record <code>LogicRunners</code> in <code>objectsToProcess</code>,
 * but <em>do not</em> invoke the LogicRunners.
 * <p/>
 * <code>objectsToProcess</code> is an <em>ordered</em> list, so objects are processed in the same order they
 * are submitted.  <br>
 * Otherwise, Lineitems would adjust Purchaseorder/Customer, which would then be
 * double-adjusted by the Purchaseorder itself.
 * 
 * <h5>Logic Phase</h5>
 * The execution of LogicRunners is then done in BeforeTransactionProcess.
 */
public class LogicEventListener implements PostInsertEventListener, PostUpdateEventListener, DeleteEventListener { 

	// NB - event listeners are shared across Hibernate session - NO INSTANCE VARIABLES HERE.  
	// Use TransactionLogicContext for state.

	/**
	 * SUBMIT, LOGIC, COMMIT, FLUSH
	 */
	public enum QueuedEventPhase {
		SUBMIT, 		// postEvents running, gathering objectsToProcess
		LOGIC,			// invoking LogicRunners from objectsToProces
		COMMIT,		// commit Constraints and Actions
		FLUSH			// final flush
	}

	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.EVENT_LISTENER);

	/**
	 * Invoked by Hibernate for all insert events.
	 */
	@Override
	public void onPostInsert(PostInsertEvent anEvent) {
		
		Object entity = anEvent.getEntity();
		
		// I am not crazy about this, since it introduces a dependency from this (high-level) class to
		// the DependencyManager, which is much lower-level. But I don't see any other way to handle
		// the problem with ClassLoaders without reworking the whole API.
		// Note that this needs to be called very early, even before creating ObjectStates, since we may
		// need to consult the logic when doing this.
		// This must also be called before eventRequiresLogic because it needs to find the logic class.
		if (entity != null && ( ! (entity instanceof Map)))
			ClassLoaderManager.getInstance().addClassLoaderFromBean(entity);

		String entityName = anEvent.getPersister().getEntityName();
		if ( ! eventRequiresLogic(entityName, anEvent.getSession().getSessionFactory()))
			return;
		LogicTransactionContext context = LogicTransactionManager.getCurrentLogicTransactionContext(anEvent);
		if (context == null)
			throw new RuntimeException("Current transaction is not one of ours. Make sure that you have defined " +
					"hibernate.current_session_context_class properly in your Hibernate configuration or persistence.xml file.");
		
		PersistentBean persBean = null;
		if (context.getQueuedEventPhase() == QueuedEventPhase.SUBMIT) {
			
			persBean = HibPersistentBeanFactory.getInstance(anEvent.getSession()).
					createPersistentBeanFromObject(anEvent.getEntity(), anEvent.getPersister());
			
			if (log.isDebugEnabled())
				log.debug("OnPostInsert: " + persBean.toString());			

			BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();			
			
			LogicRunner logicRunner = 
				businessLogicFactory.createLogicRunner(context, persBean, null, Verb.INSERT, LogicSource.USER, null, null);
			addToObjectsToProcess(logicRunner);
			context.registerLogicRunner(logicRunner);
		}

		// Finally notify the event mechanism
		if (persBean == null)
			persBean = HibPersistentBeanFactory.getInstance(anEvent.getSession()).
				createPersistentBeanFromObject(anEvent.getEntity(), anEvent.getPersister());

		context.addObjectEvent(persBean, ObjectEvent.EventType.INSERT, anEvent.getSession());
	}

	/**
	 * Invoked by Hibernate for all updates.
	 */
	@Override
	public void onPostUpdate(PostUpdateEvent anEvent) {
		
		Object entity = anEvent.getEntity();
		
		// See comment in onPostInsert
		if ( ! (entity instanceof Map))
			ClassLoaderManager.getInstance().addClassLoaderFromBean(entity);

		if ( ! eventRequiresLogic(anEvent.getPersister().getEntityName(), anEvent.getSession().getSessionFactory()))
			return;
		LogicTransactionContext context = LogicTransactionManager.getCurrentLogicTransactionContext(anEvent);
		if (context == null)
			throw new RuntimeException("Current transaction is not one of ours. Make sure that you have defined " +
					"hibernate.transaction.factory_class properly in your Hibernate configuration.");

		PersistentBean persBean = null;
		if (context.getQueuedEventPhase() == QueuedEventPhase.SUBMIT) {
			
			Object[] oldState = anEvent.getOldState();
			if (oldState == null) { // This can happen if an object is updated e.g. reconnected to the database
//				We need to catch this. It came up during a debugging session
//				with Cesar, who was using session.update to save a detached object. The problem in that case is that
//				the row gets overwritten without any pre-select, which means that oldState is of course null.
				throw new RuntimeException("An update event has no old state. This can be caused by saving a detached bean using update, " +
						"without doing a merge first. ABL does not support the \"blind\" updating of objects, as it prevents the use of old values. " +
						"You can call update on a bean (that has logic) only if it has been fetched in the scope of the current transaction, " +
						"or it has been merged, e.g. customer = (Customer)session.merge(customer);\n" +
						"In this case, the offending bean was: " + anEvent.getEntity());
			}
			
			HibPersistentBeanFactory beanFactory = HibPersistentBeanFactory.getInstance(anEvent.getSession());
			persBean = beanFactory.
					createPersistentBeanFromObject(anEvent.getEntity(), anEvent.getPersister());
			PersistentBean oldBean = HibPersistentBeanFactory.createPersistentBeanCopyFromState(anEvent.getOldState(), 
					entity, anEvent.getPersister(), anEvent.getSession());

			if (log.isDebugEnabled()) 
				log.debug("OnPostUpdate: entity" + persBean);
			
			BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();			
			
			LogicRunner logicRunner = businessLogicFactory.createLogicRunner(context, persBean, 
					oldBean, Verb.UPDATE, LogicSource.USER, null, null);
			addToObjectsToProcess(logicRunner);
			context.registerLogicRunner(logicRunner);
		}

		// Finally notify the event mechanism
		if (persBean == null)
			persBean = HibPersistentBeanFactory.getInstance(anEvent.getSession()).
				createPersistentBeanFromObject(anEvent.getEntity(), anEvent.getPersister());

		context.addObjectEvent(persBean, ObjectEvent.EventType.UPDATE, anEvent.getSession());
	}

	/**
	 * Common implementation for both DeleteEvent methods. We capture the state of the deleted object, and store it in
	 * the LogicTransactionContext, so that, during rules processing, we can tell whether an object has been deleted.
	 */
	private static void handleDelete(DeleteEvent anEvent) {
		
		String entityName = anEvent.getEntityName();
		
		// The event we receive may not contain an entity name, in which case we have to figure out
		// the entity name from the bean itself.
		if (entityName == null) {
			Object bean = anEvent.getObject();
			entityName = ClassNameUtil.getEntityNameForBean(bean);
		}
		
		// See comment in onPostInsert
		if ( ! (anEvent.getObject() instanceof Map))
			ClassLoaderManager.getInstance().addClassLoaderFromBean(anEvent.getObject());

		if ( ! eventRequiresLogic(entityName, anEvent.getSession().getSessionFactory()))
			return;
		
		LogicTransactionContext context = LogicTransactionManager.getCurrentLogicTransactionContext(anEvent);
		
		if (context == null)
			throw new RuntimeException("Current transaction is not one of ours. Make sure that you have defined " +
					"hibernate.transaction.factory_class properly in your Hibernate configuration.");
		
		SessionFactoryImpl sfi = (SessionFactoryImpl)anEvent.getSession().getSessionFactory();
		EntityPersister persister = sfi.getEntityPersister(entityName);
		PersistentBean persBean = null;
		if (context.getQueuedEventPhase() == QueuedEventPhase.SUBMIT) {
			
			HibPersistentBeanFactory beanFactory = HibPersistentBeanFactory.getInstance(anEvent.getSession());
			persBean = beanFactory.createPersistentBeanFromObject(anEvent.getObject(), persister);

			// In some cases, we can see more than one event for the same object, for instance if a child object is being
			// cascade-deleted, and it itself has a Cascade=ALL to its parent (which would be a rather questionable mapping,
			// but it could happen).
			PersistentBean deletedState = context.getDeletedObjectState(persBean);
			if (deletedState != null) {
				if (log.isDebugEnabled())
					log.debug("Deleted object " + anEvent.getObject() + " has already been deleted -- ignoring");
				return;
			}
			
			context.addDeletedObjectState(persBean);
			
			if (log.isInfoEnabled())
				log.info("OnPostDelete: entity " + persBean);
	
			// We make another PersistentBean because that one may get modified by formulas and such.
			// Not entirely sure what that means?
			PersistentBean persBean2 = beanFactory.
					createPersistentBeanFromObject(anEvent.getObject(), persister);
			
			BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();			
			
			LogicRunner logicRunner = businessLogicFactory.createLogicRunner(context, persBean, 
					persBean2, Verb.DELETE, LogicSource.USER, null, null);
			addToObjectsToProcess(logicRunner);
			context.registerLogicRunner(logicRunner);
		}

		// Finally notify the event mechanism
		if (persBean == null)
			persBean = HibPersistentBeanFactory.getInstance(anEvent.getSession()).
					createPersistentBeanFromObject(anEvent.getObject(), persister);

		context.addObjectEvent(persBean, ObjectEvent.EventType.DELETE, anEvent.getSession());
	}
	
	@Override
	public void onDelete(DeleteEvent event) {
		handleDelete(event);		
	}

	@Override
	public void onDelete(DeleteEvent event, @SuppressWarnings("rawtypes") Set transientEntities) {
		handleDelete(event);		
	}

	/**
	 * 
	 * @param aLogicRunner - added to objectsToProcess (debug note iff in objectsWeHaveSeen)
	 */
	private static void addToObjectsToProcess(LogicRunner aLogicRunner) {
		LogicTransactionContext context = aLogicRunner.getContext();

		List<LogicRunner> objectsToProcess = context.getObjectsToProcess();
		if (objectsToProcess.contains(aLogicRunner))
			log.debug("Caution - logic runner already seen");
		objectsToProcess.add(aLogicRunner);
	}

	/**
	 * Determine whether an event is for an object that involves business logic. This does necessarily
	 * mean that the object must have business logic itself, for instance the child class of a sum
	 * may not have any business logic of its own, yet it must be taken into account.
	 */
	private static boolean eventRequiresLogic(String entityName, SessionFactory sessionFactory) {
		MetaModel metaModel = MetaModelFactory.getHibernateMetaModel(sessionFactory);
		MetaEntity metaEntity = metaModel.getMetaEntity(entityName);
		return RuleManager.getInstance(metaModel).entityIsRelevant(metaEntity);
	}

	private static final long serialVersionUID = 1L;

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicEventListener.java 898 2012-03-06 07:40:51Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 