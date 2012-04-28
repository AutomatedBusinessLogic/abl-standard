package com.autobizlogic.abl.logic;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.impl.SessionImpl;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.PersistentBeanCache;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.hibernate.HibernateUtil;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.session.LogicTransactionManager;
import com.autobizlogic.abl.text.LogicMessageFormatter;
import com.autobizlogic.abl.text.MessageName;
import com.autobizlogic.abl.util.BeanMap;
import com.autobizlogic.abl.util.ClassNameUtil;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * An object providing state/services for Business Logic Components.
 * <p>
 * 
 * This object is optionally made available to your Business Logic Components
 *  through a variable marked with the LogicContext annotation, like this:
 *  <blockquote>
 * <code>
 * 	@LogicContextObject
 * LogicContext logicContext = null
 * </code>
 * </blockquote>
 * <p>
 * 
 * It provides state information your Business Logic may require, such as
 * <ol>
 * <li>Nest Level - distinguish changes from clients (vs. Forward Chain)</li>
 * <li>Verb - distinguish Insert, Update and Delete</li>
 * <li>Session - for Hibernate retrieval/meta data access</li>
 * <li>Current/Old State - convenient for passing this object to Logic Extensions</ol>
 * </ol>
 * <p>
 * Services are provided for insert, update and delete of objects
 * from your Business Logic Components.
 * <blockquote>
 * <strong>Important:</strong> use these (rather than native Hibernate services),
 *  to assure that the business logic is executed for updated objects.
 *  </blockquote>
 * 
 */
public class LogicContext {
	protected int logicNestLevel;
	protected Verb verb;
	protected Verb initialVerb;  							// so Logic Class can test in Commit events
	protected Session session;
	protected Map<String, Object> properties = new HashMap<String, Object>();
	protected PersistentBean currentState; 
	protected PersistentBean oldState;
	protected LogicRunner logicRunner;
	protected PersistentBeanCache oldStateCache = new PersistentBeanCache();
	protected static final LogicLogger log = LogicLogger.getLogger(LoggerName.RULES_ENGINE);

	public static final String nullValue = "Explict Null Assignment";

	public LogicContext() {
		super();
	}

	/**
	 * <strong>Important</strong>: use this method to save a new object in an action.
	 * Do not use session methods, as they will not engage logic during commit processing) as further explained in the link.
	 * 
	 * The saved object does <em>not</em> need to be this.currentState.
	 * 
	 * @link <a href="http://www.automatedbusinesslogic.com/architecture/extensibility"> Business Logic updates</a>
	 * @param aDomainObject is inserted, <em>with logic execution</em>
	 */
	public void insert(Object aDomainObject) {

		if (( ! (aDomainObject instanceof PersistentBean)) && (aDomainObject instanceof Map))
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_mapRequiresName));
		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "save"));
		PersistentBean persBean = null;
		if (aDomainObject instanceof PersistentBean) {
			persBean = (PersistentBean) aDomainObject;
			session.saveOrUpdate(persBean.getEntityName(), persBean.getEntity());				
		} else {
			session.save(aDomainObject);
			persBean = HibPersistentBeanFactory.getInstance(session).
					createPersistentBeanFromEntity(aDomainObject, null);
		}

		LogicContext savedLogicContext = saveLogicContext();
		LogicTransactionContext context = logicRunner.getContext();
		LogicRunner calledLogicRunner = BusinessLogicFactoryManager.getBusinessLogicFactory().
				getLogicRunner(context, persBean, null, Verb.INSERT, LogicSource.LOGIC, logicRunner, null);
		if (calledLogicRunner != null)
			calledLogicRunner.insert();
		restoreLogicContext(savedLogicContext);
	}

	/**
	 * <strong>Important</strong>: use this method to save a new object, <em>not</em> session methods.
	 * Do not use session methods, as they will not engage logic during commit processing) as further explained in the link.
	 * Save a persistent bean.
	 * @param aDomainObject The bean to save
	 * @param entityName The entity name for the bean - required if the bean is a Map.
	 */
	public void insert(String entityName, Object aDomainObject) {
		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "save"));
		PersistentBean persBean = HibPersistentBeanFactory.getInstance(session).
				createPersistentBeanFromEntity(aDomainObject, entityName);
		
		session.save(persBean.getEntityName(), persBean.getEntity());

		LogicContext savedLogicContext = saveLogicContext();
		LogicTransactionContext context = logicRunner.getContext();
		LogicRunner calledLogicRunner = BusinessLogicFactoryManager.getBusinessLogicFactory().
				getLogicRunner(context, persBean, null, Verb.INSERT, LogicSource.LOGIC, logicRunner, null);
		if (calledLogicRunner != null)
			calledLogicRunner.insert();
		restoreLogicContext(savedLogicContext);
	}
	
	/**
	 * This method is deprecated: use insert instead.
	 */
	@Deprecated
	public void save(Object aDomainObject) {
		insert(aDomainObject);
	}
	
	/**
	 * This method is deprecated: use insert instead.
	 */
	@Deprecated
	public void save(String entityName, Object aDomainObject) {
		insert(entityName, aDomainObject);
	}
	
	/**
	 * <b>Important</b>: use this method on any persistent object you intend to update
	 * in an action <b>before</b> making any updates to it. Any update of a persistent
	 * object in an action must be bracketed between touch() and update(). Note that this
	 * only applies to updates -- inserts and deletes do not require calls to touch().
	 * @param aDomainObject Either a persistent POJO or a PersistentBean
	 */
	public void touch(Object aDomainObject) {
		if (aDomainObject == null)
			return;
		if (( ! (aDomainObject instanceof PersistentBean)) && (aDomainObject instanceof Map))
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_mapRequiresName));
		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "touch"));

		PersistentBean persBean = null;
		if (aDomainObject instanceof PersistentBean) {
			persBean = HibPersistentBeanFactory.copyPersistentBean((PersistentBean) aDomainObject);
		} else {
			String entityName = session.getEntityName(aDomainObject);
			EntityPersister ep = ((SessionImpl)session).getEntityPersister(entityName, aDomainObject);
			persBean = HibPersistentBeanFactory.getInstance(session).createPersistentBeanCopyFromObject(aDomainObject, ep);
		}
		oldStateCache.addBean(persBean);
	}

	/**
	 * <b>Important</b>: use this method on any persistent object you intend to update
	 * in an action <b>before</b> making any updates to it. Any update of a persistent
	 * object in an action must be bracketed between touch() and update(). Note that this
	 * only applies to updates -- inserts and deletes do not require calls to touch().
	 * @param aDomainObject Either a persistent POJO or a PersistentBean
	 */
	public void touch(String entityName, Object aDomainObject) {
		if (aDomainObject == null)
			return;
		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "touch"));

		PersistentBean persBean = null;
		if (aDomainObject instanceof PersistentBean) {
			persBean = HibPersistentBeanFactory.copyPersistentBean((PersistentBean) aDomainObject);
		} else {
			EntityPersister ep = ((SessionImpl)session).getEntityPersister(entityName, aDomainObject);
			persBean = HibPersistentBeanFactory.getInstance(session).createPersistentBeanCopyFromObject(aDomainObject, ep);
		}
		oldStateCache.addBean(persBean);
	}

	/**
	 * <strong>Important</strong> use this method to save an updated object in an action.
	 * Do not use session methods, as they will not engage logic during commit processing).
	 * @param aDomainObject is updated, <em>with logic execution</em>. This can only be a Pojo.
	 */
	public void update(Object aDomainObject) {
		if (aDomainObject == null)
			return;

		if (( ! (aDomainObject instanceof PersistentBean)) && (aDomainObject instanceof Map))
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_mapRequiresName));
		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "update"));
		PersistentBean persBean = null;
		if (aDomainObject instanceof PersistentBean) {
			persBean = (PersistentBean) aDomainObject;
			session.update(persBean.getEntityName(), persBean.getEntity());				
		} else {
			session.update(aDomainObject);
			persBean = HibPersistentBeanFactory.getInstance(session).
					createPersistentBeanFromEntity(aDomainObject, null);
		}

		//PersistentBean oldPersBean = HibernateUtil.getOldStateForObject(aDomainObject, session);
		PersistentBean oldPersBean = oldStateCache.getBean(persBean.getEntityName(), persBean.getPk());
		if (oldPersBean == null)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_updateWithoutTouch,
					persBean.getEntityName() + "[" + persBean.getPk() + "]"));

		LogicContext savedLogicContext = saveLogicContext();
		LogicTransactionContext context = logicRunner.getContext();
		LogicRunner calledLogicRunner = BusinessLogicFactoryManager.getBusinessLogicFactory().
				getLogicRunner(context, persBean, oldPersBean, Verb.UPDATE, LogicSource.LOGIC, logicRunner, null);
		if (calledLogicRunner != null)
			calledLogicRunner.update();
		restoreLogicContext(savedLogicContext);		
	}

	/**
	 * <strong>Important</strong> use this method to save an updated object in an action.
	 * Do not use session methods, as they will not engage logic during commit processing).
	 * @param aDomainObject The persistent bean
	 * @param entityName The entity name - required if the bean is a Map.
	 */
	public void update(String entityName, Object aDomainObject) {
		if (aDomainObject == null)
			return;

		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "update"));
		session.save(entityName, aDomainObject);
		PersistentBean persBean = HibPersistentBeanFactory.getInstance(session).
				createPersistentBeanFromEntity(aDomainObject, entityName);

		LogicContext savedLogicContext = saveLogicContext();
		LogicTransactionContext context = logicRunner.getContext();

		//PersistentBean oldPersBean = HibernateUtil.getOldStateForObject(aDomainObject, session);
		PersistentBean oldPersBean = oldStateCache.getBean(entityName, persBean.getPk());
		if (oldPersBean == null)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_updateWithoutTouch,
					entityName + "[" + persBean.getPk() + "]"));

		LogicRunner calledLogicRunner = BusinessLogicFactoryManager.getBusinessLogicFactory().
				getLogicRunner(context, persBean, oldPersBean, Verb.UPDATE, LogicSource.LOGIC, logicRunner, null);
		if (calledLogicRunner != null)
			calledLogicRunner.update();
		restoreLogicContext(savedLogicContext);
	}

	/**
	 * <strong>Important</strong> use this method to delete an object in an action.
	 * Do not use session methods, as they will not engage logic during commit processing).
	 * @param aDomainObject is deleted, <em>with logic execution</em>. This must be a Pojo.
	 */
	public void delete(Object aDomainObject) {
		if (aDomainObject == null)
			return;

		if (( ! (aDomainObject instanceof PersistentBean)) && (aDomainObject instanceof Map))
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_mapRequiresName));
		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "delete"));
		PersistentBean persBean = null;
		if (aDomainObject instanceof PersistentBean) {
			persBean = (PersistentBean) aDomainObject;
			if (persBean.getMap() != null)
				session.delete(persBean.getEntityName(), persBean.getMap());				
		} else {
			session.delete(aDomainObject);
			persBean = HibPersistentBeanFactory.getInstance(session).
					createPersistentBeanFromEntity(aDomainObject, null);
		}

		LogicContext savedLogicContext = saveLogicContext();
		LogicTransactionContext context = logicRunner.getContext();
		LogicRunner calledLogicRunner = BusinessLogicFactoryManager.getBusinessLogicFactory().
				getLogicRunner(context, persBean, null, Verb.DELETE, LogicSource.LOGIC, logicRunner, null);
		if (calledLogicRunner != null) {
			calledLogicRunner.delete();
		}
		restoreLogicContext(savedLogicContext);		
	}

	/**
	 * <strong>Important</strong> use this method to delete an object in an action.
	 * Do not use session methods, as they will not engage logic during commit processing).
	 * @param aDomainObject The persistent bean to delete
	 * @param entityName The entity name for the bean. Required if the bean is a Map.
	 */
	public void delete(String entityName, Object aDomainObject) {
		if (aDomainObject == null)
			return;

		if ( getLogicRunner().getExecutionState() != LogicRunnerPhase.ACTIONS && getLogicRunner().getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS)
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_onlyInAction, "delete"));
		session.delete(entityName, aDomainObject);

		PersistentBean persBean = HibPersistentBeanFactory.getInstance(session).
				createPersistentBeanFromEntity(aDomainObject, entityName);

		LogicContext savedLogicContext = saveLogicContext();
		LogicTransactionContext context = logicRunner.getContext();
		LogicRunner calledLogicRunner = BusinessLogicFactoryManager.getBusinessLogicFactory().
				getLogicRunner(context, persBean, null, Verb.DELETE, LogicSource.LOGIC, logicRunner, null);
		if (calledLogicRunner != null)
			calledLogicRunner.delete();
		restoreLogicContext(savedLogicContext);		
	}

	/**
	 * Internal use only.
	 * @return copy of this
	 */
	public LogicContext saveLogicContext() {
		LogicContext rtnLogicContext = new LogicContext();
		rtnLogicContext.setCurrentState(currentState);
		rtnLogicContext.setOldState(oldState);
		rtnLogicContext.setVerb(verb);
		rtnLogicContext.setInitialVerb(initialVerb);
		rtnLogicContext.setLogicNestLevel(logicNestLevel);
		rtnLogicContext.setLogicRunner(logicRunner);
		return rtnLogicContext;
	}

	/**
	 * Internal use only.
	 * 
	 * @param aLogicContext
	 */
	public void restoreLogicContext(LogicContext aLogicContext) {
		setCurrentState(aLogicContext.getCurrentState());
		setOldState(aLogicContext.getOldState());
		setVerb(aLogicContext.getVerb());
		setInitialVerb(aLogicContext.getInitialVerb());
		setLogicNestLevel(aLogicContext.getLogicNestLevel());
		setLogicRunner(aLogicContext.getLogicRunner());
	}

	/**
	 * Get the current nesting level. This can be accessed by business logic to determine
	 * how deep into the call they are. Normally, the only test is whether the nesting level
	 * is zero or not. It should be highly unusual to test for anything else.
	 * @return
	 */
	public int getLogicNestLevel() {
		return logicNestLevel;
	}

	/**
	 * 
	 * @param aChildLogicRunner
	 * @param anAttributeName
	 * @return true if anAttributeName's value changed from aChildLogicRunner's current/priorDomainObject (== true for insert)
	 */
	public boolean isAttributeChanged(String anAttributeName) {
		if (getVerb() == Verb.INSERT )
			return true;
		else if (getVerb() == Verb.DELETE )
			return false;
		else
			return LogicSvcs.isAttributeChanged(logicRunner, anAttributeName);
	}

	/**
	 * Determine whether a new value is being assigned on the given role.
	 * This can only be called on attributes that are single-valued entities.
	 * This method will look at the value of the given attribute in the current state
	 * and in the old state. If the new value is not null, and is different than the
	 * old value, it will return true, otherwise false.
	 */
	public boolean isAttachingToParent(String aParentPropertyName) {

		BeanMap beanMap = new BeanMap(currentState);

		if ( ! beanMap.containsKey(aParentPropertyName)) {
			String className = HibernateUtil.getEntityNameForObject(currentState);
			throw new RuntimeException(LogicMessageFormatter.getMessage(MessageName.logicContext_noSuchAttrib, 
					new Object[]{aParentPropertyName, className}));
		}

		Object currentParent = beanMap.get(aParentPropertyName);
		if (currentParent == null)
			return false;

		// If there is no known previous state, then by definition we must be attaching
		if (oldState == null)
			return true;

		BeanMap oldBeanMap = new BeanMap(oldState);
		Object oldParent = oldBeanMap.get(aParentPropertyName);
		return ! HibernateUtil.beansHaveSamePK(currentParent, oldParent, session);
	}

	public void setLogicNestLevel(int aLevel) {
		logicNestLevel = aLevel;
	}

	public void setVerb(Verb verb) {
		this.verb = verb;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	/**
	 * 
	 * @return Hibernate session, for retrieving data - <strong>not</strong> for update within Logic
	 * @see #save(Object)
	 */
	public Session getSession() {
		return session;
	}

	/**
	 * Get the entity name for the given persistent bean. This only works for Pojo objects,
	 * not for Map objects.
	 * @param anObject The persistent bean
	 * @return The entity name (i.e. "real" class name as opposed to proxy class name) for anObject
	 */
	@SuppressWarnings("unchecked")
	static public String getEntityNameForObject(Object anObject) {
		if (anObject instanceof PersistentBean)
			return ((PersistentBean)anObject).getEntityName();
		else if (anObject instanceof Map)			// FIXME temp val
			return (String)((Map<String, Object>)anObject).get("$type$");
		else
			return ClassNameUtil.getEntityNameForBean(anObject);
	}


	/**
	 * 
	 * @return Properties, for extensibility - life cycle is transaction (not session).
	 * If the given name does not exist in the user properties, null is returned.
	 */
	public Object getUserProperty(String name) {
		return properties.get(name);
	}

	/**
	 * Sets the user property to the given value.
	 * @param name The name of the property
	 * @param value The value for the property
	 */
	public void setUserProperty(String name, Object value) {
		properties.put(name, value);
	}

	/**
	 * Unset the given user property.
	 * @param name The name of the property to unset
	 * @return The old value of the property, or null if none
	 */
	public Object unsetUserProperty(String name) {
		return properties.remove(name);
	}

	/*
	 * internal use - system state for insert, update and delete operations
	 */
	public void setLogicRunner(LogicRunner aLogicRunner) {
		this.logicRunner = aLogicRunner;
	}

	/**
	 * Internal method. Get the LogicRunner for this context.
	 */
	public LogicRunner getLogicRunner() {
		return logicRunner;
	}

	/**
	 * 
	 * @return 
	 * @see Verb
	 */
	public Verb getVerb() {
		return verb;
	}

	/**
	 * 
	 * @return Verb of initial change (eg, object can be inserted/deleted, then update <adjusted> by subsequent objects in transaction
	 * @see Verb
	 */
	public Verb getInitialVerb() {
		return initialVerb;
	}

	public void setInitialVerb(Verb initialVerb) {
		this.initialVerb = initialVerb;
	}

	/**
	 * 
	 * @return current domain object (pojo), provided for generic extended rules
	 */
	public PersistentBean getCurrentState() {
		return currentState;
	}

	public void setCurrentState(PersistentBean current) {
		this.currentState = current;
	}

	/**
	 * 
	 * @return previous domain object (pojo), provided for generic extended rules
	 */
	public PersistentBean getOldState() {
		return this.oldState;
	}

	public void setOldState(PersistentBean anOldState) {
		this.oldState = anOldState;
	}

	/**
	 * Get the current use case name, which is actually retrieved from the current LogicTransactionContext.
	 */
	public String getUseCaseName() {
		return logicRunner.getContext().getUseCaseName();
	}

	/**
	 * Set the current use case name, which is actually stored in the current LogicTransactionContext.
	 */
	public void setUseCaseName(String useCaseName) {
		logicRunner.getContext().setUseCaseName(useCaseName);
	}

	/**
	 * Get the current use case name for the transaction.
	 * @param tx The current transaction
	 * @return Null if there is no current use case name, or if the current transaction is not a ABL transaction.
	 * Otherwise the current use case name.
	 */
	public static String getCurrentUseCaseName(Session aSession, Transaction tx) {
		LogicTransactionContext ctxt = LogicTransactionManager.getCurrentLogicTransactionContextForTransaction(tx, aSession);
		if (ctxt == null) {
			log.warn(LogicMessageFormatter.getMessage(MessageName.logicContext_notABLTx));
			return null;
		}
		return ctxt.getUseCaseName();
	}

	/**
	 * Set the current use case name
	 * @param tx The current transaction
	 * @param useCaseName The desired use case name
	 */
	public static void setCurrentUseCaseName(Session aSession, Transaction tx, String useCaseName) {
		LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
		LogicTransactionContext ctxt = LogicTransactionManager.getCurrentLogicTransactionContextForTransaction(tx, aSession);
		if (ctxt == null) {
			log.warn(LogicMessageFormatter.getMessage(MessageName.logicContext_notABLTxSet, useCaseName));
			return;
		}
		ctxt.setUseCaseName(useCaseName);
		if (_logger.isInfoEnabled())  _logger.info ("\n\n\n#BEGIN USE CASE: " + useCaseName + "   **********\n");

	}

	/**
	 * 
	 * @return reason this logic execution initiated
	 */
	public LogicSource getLogicSource() {
		return getLogicRunner().getLogicSource();
	}

	/**
	 * Get the name of role that initiated this logic execution.
	 */
	public String getViaRole() {
		MetaRole callingRole = getLogicRunner().getCallingRole();
		if (callingRole != null) 
			return callingRole.getRoleName();

		return "";
	}

	/**
	 * Prints DEBUG console line with key, aMsg, Domain Object values'
	 * 
	 * @param aMsg
	 */
	public void logDebug(String aMsg) {
		if (log.isDebugEnabled())
			log.debug ("logDebug: " + aMsg, logicRunner);

	}
	
	@Override
	public String toString() {
		return getLogicRunner().toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicContext.java 1243 2012-04-22 18:03:02Z max@automatedbusinesslogic.com $";

}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 