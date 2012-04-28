package com.autobizlogic.abl.session;

import java.io.Serializable;
//import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.proxy.HibernateProxy;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.hibernate.LogicEventListener.QueuedEventPhase;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicProcessingState;
import com.autobizlogic.abl.rule.ActionRule;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.event.GlobalLogicEventHandler;
import com.autobizlogic.abl.event.LogicEvent;
import com.autobizlogic.abl.event.TransactionSummary;
import com.autobizlogic.abl.event.ObjectEvent.EventType;
import com.autobizlogic.abl.util.LogicLogger;

/**
 * The class that holds all the information needed by the business rules engine
 * to keep track of the current state of things. This object is created whenever
 * a Hibernate transaction is started, and it goes away once the transaction ends.
 * <br>
 * While the transaction is open, the current instance of this class can be retrieved
 * using LogicTransactionManager:<br>
 * <code>LogicTransactionManager.getCurrentLogicTransactionContext()</code>
 */
public class LogicTransactionContext {
	
	private int stackLevel = 0;
	
	private Session topLevelSession;

	/**
	 * A handle to the current Hibernate session.
	 */
	private Session session;
	
	private QueuedEventPhase queuedEventPhase = QueuedEventPhase.SUBMIT;
	
	private String useCaseName = null;
	
	private List<LogicRunner> objectsToProcess = new CopyOnWriteArrayList<LogicRunner>();
	
	/**
	private List<LogicRunner> objectsToProcess = new Vector<LogicRunner>();
	
	/**
	 * Keep track of objects that have been deleted. The main key is the entity name, and in the
	 * value, the key is the PK and the value is the PersistentBean itself.
	 */
	private Map<String, Map<Serializable, PersistentBean>> deletedObjectStates = new HashMap<String, Map<Serializable, PersistentBean>>();

	/**
	 * Keep track of all the Hibernate proxies in which we have introduced our own proxies.
	 * This allows us to clean them up at the end of the transaction.
	 * 
	 * Note : we use a Vector and not a Set because HashSet calls hashCode on all collection members, and
	 * that can have unfortunate effects with Hibernate proxies.
	 */
//	private Collection<Object> beanProxies = new Vector<Object>();

	
	/**
	 * Objects processed by LogicRunner at nestLevel = 0
	 */
	private Set<PersistentBean> userSubmittedObjects = new HashSet<PersistentBean>();
		
	private LogicRunner masterRunner = null;  // first runner we see
	
	/**
	 * for key(domain object), set of actions already run
	 */
	private Map<Object, Set<ActionRule>> executedActions = new HashMap<Object, Set<ActionRule>>();
	
	/**
	 * Keep track of all modified objects within this transaction.
	 */
	private TransactionSummary transactionSummary = new TransactionSummary();
		
	@SuppressWarnings("unused")
	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.PERSISTENCE);

	
	public void incrementStackLevel() {
		stackLevel++;
	}
	
	public void decrementStackLevel() {
		stackLevel--;
		if (stackLevel < 0)
			throw new LogicException("LogicTransactionContext stack level is less than 0!");
	}
	
	public int getStackLevel() {
		return stackLevel;
	}
	
	public Session getTopLevelSession() {
		return topLevelSession;
	}
	
	public void setTopLevelSession(Session sess) {
		topLevelSession = sess;
	}
	
	/**
	 * Get the Hibernate session for this context
	 */
	public Session getSession() {
		return session;
	}
	
	/**
	 * Get the MetaModel for this context
	 */
	public MetaModel getMetaModel() {
		return MetaModelFactory.getHibernateMetaModel(session.getSessionFactory());
	}
	
	/**
	 * Initialize this context with a session
	 */
	public void setSession(Session session) {
		this.session = session;
	}
	
	public LogicRunner getMasterRunner() {
		return masterRunner;
	}

	public void setMasterRunner(LogicRunner masterRunner) {
		this.masterRunner = masterRunner;
	}

	public Set<PersistentBean> getUserSubmittedObjects() {
		return userSubmittedObjects;
	}

	public void setUserSubmittedObjects(Set<PersistentBean> userSubmittedObjects) {
		this.userSubmittedObjects = userSubmittedObjects;
	}
	
	/**
	 * Get all the logic runners currently lined up to run.
	 */
	public List<LogicRunner> getObjectsToProcess() {
		return objectsToProcess;
	}
	
	/**
	 * Add the given LogicRunner to the list of objects to process.
	 * @param logicRunner A LogicRunner that will get added to the end of the queue.
	 */
	public void addObjectToProcess(LogicRunner logicRunner) {
		objectsToProcess.add(logicRunner);
	}

	/**
	 * Look through all the logic runners that have not yet run for any that is for
	 * the given bean.
	 * @param bean The bean in question
	 * @return Null if no logic runner is queued for this bean, otherwise the logic runner.
	 */
	public LogicRunner findObjectToProcess(PersistentBean bean) {

		if (bean == null)
			return null;
		for (LogicRunner runner : objectsToProcess) {
			if (bean.equals(runner.getCurrentDomainObject()) && runner.getLogicProcessingState() == LogicProcessingState.QUEUED)
				return runner;
		}
		return null;
	}
	
	/**
	 * Determine whether the given logic runner is queued for execution.
	 */
	public boolean logicRunnerIsQueued(LogicRunner logicRunner) {
		if ( ! objectsToProcess.contains(logicRunner))
			return false;
		if (logicRunner.getLogicProcessingState() != LogicProcessingState.QUEUED)
			return false;
		
		return true;
	}

	/**
	 * Keep track of all logic runners created within the scope of a transaction. The idea is to keep
	 * only the latest LogicRunner for each instance of each class, so that we can reuse them at the end
	 * for commit actions and commit constraints.
	 */
	private Map<String, Map<Serializable, LogicRunner>> allLogicRunners = new HashMap<String, Map<Serializable, LogicRunner>>();
	
	/**
	 * This must be called by anyone who creates a LogicRunner. It keeps track of them so that,
	 * at the end of a transaction, we can run the commit-time actions and constraints.
	 */
	public void registerLogicRunner(LogicRunner runner) {
		String entityName = runner.getCurrentDomainObject().getEntityName();
		Map<Serializable, LogicRunner> runnersForClass = allLogicRunners.get(entityName);
		if (runnersForClass == null) {
			runnersForClass = new HashMap<Serializable, LogicRunner>();
			allLogicRunners.put(entityName, runnersForClass);
		}
		Serializable pk = runner.getCurrentDomainObject().getPk();
		runnersForClass.put(pk, runner); // Note that this will overwrite a LogicRunner already there for this object. This is as intended.
	}
	
	/**
	 * Get the LogicRunners for all objects touched during the transaction.
	 */
	public Set<LogicRunner> getAllLogicRunners() {
		Set<LogicRunner> allRunners = new HashSet<LogicRunner>();
		for (Map<Serializable, LogicRunner> runnerEntry : allLogicRunners.values()) {
			allRunners.addAll(runnerEntry.values());
		}
		return allRunners;
	}
	
	/**
	 * See if we have a LogicRunner for the given PersistentBean.
	 * @param aBean The bean to check for
	 * @return <em>any</em> LogicRunner whose currentDomainObject matches aBean, or null
	 * if none is found.
	 */
	public LogicRunner findLogicRunner(PersistentBean aBean) {

		if (aBean == null)
			return null;

		for (LogicRunner eachLogicRunner: objectsToProcess) {
			PersistentBean eachLogicRunnerCurrentState = eachLogicRunner.getCurrentDomainObject();
			if (eachLogicRunnerCurrentState.equals(aBean))
				return eachLogicRunner;
		}
		return null;
	}
	
	/**
	 * Find the most-recently-queued LogicRunner for the given PersistentBean.
	 * @return Null if none is found.
	 */
	public LogicRunner findNewestLogicRunner(PersistentBean aBean) {
		Vector<LogicRunner> revObjectsToProcess = new Vector<LogicRunner>(objectsToProcess);
		Collections.reverse(revObjectsToProcess);
		for (LogicRunner eachLogicRunner: revObjectsToProcess) {
			PersistentBean eachLogicRunnerCurrentState = eachLogicRunner.getCurrentDomainObject();
			if (eachLogicRunnerCurrentState.equals(aBean))
				return eachLogicRunner;
		}
		return null;
	}
	
	/**
	 * Record the state of a deleted object for the transaction.
	 */
	public void addDeletedObjectState(PersistentBean bean) {
		if (bean == null)
			throw new RuntimeException("Cannot add null object to deleted object states");
		Map<Serializable, PersistentBean> deleted = deletedObjectStates.get(bean.getEntityName());
		if (deleted == null) {
			deleted = new HashMap<Serializable, PersistentBean>();
			deletedObjectStates.put(bean.getEntityName(), deleted);
		}
		deleted.put(bean.getPk(), bean);
	}
	
	/**
	 * Add an object event to the transaction summary.
	 * @param bean The bean
	 * @param pk The bean's primary key
	 * @param eventType What type of event this is
	 */
	public void addObjectEvent(PersistentBean bean, EventType eventType, Session theSession) {
		
		transactionSummary.addObjectEvent(bean, eventType, theSession);
	}

	/**
	 * Get the TransactionSummary for this transaction.
	 */
	public TransactionSummary getTransactionSummary() {
		return transactionSummary;
	}
	
	/**
	 * Get the deleted ObjectState for the given object.
	 * @param pBean The entity to check for
	 * @return The PersistentBean for the given object at the moment it was deleted, or null
	 * if the object is null, or was not deleted.
	 */
	public PersistentBean getDeletedObjectState(PersistentBean pBean) {
		if (pBean == null)
			return null;
		Map<Serializable, PersistentBean> deleted = deletedObjectStates.get(pBean.getEntityName());
		if (deleted == null)
			return null;
		
		return deleted.get(pBean.getPk());
	}
	
	/**
	 * Has the given object been deleted within this transaction? This works with a full object or a proxy.
	 * @return True if the given object was deleted within this transaction, false otherwise or if the
	 * given object is null.
	 */
	public boolean objectIsDeleted(PersistentBean pBean) {
		return getDeletedObjectState(pBean) != null;
	}
	
//	public void registerBeanProxy(Object proxy) {
//		beanProxies.add(proxy);
//	}
	
	/**
	 * Get access to the cache of beans that have been proxied with BeanProxyHandler.
	 * This is purely internal.
	 */
//	public Collection<Object> getBeanProxies() {
//		return beanProxies;
//	}

	public QueuedEventPhase getQueuedEventPhase() {
		return queuedEventPhase;
	}

	public void setQueuedEventPhase(QueuedEventPhase queuedEventPhase) {
		this.queuedEventPhase = queuedEventPhase;
	}

	public Map<Object, Set<ActionRule>> getExecutedActions() {
		return executedActions;
	}

	public void setExecutedActions(Map<Object, Set<ActionRule>> executedActions) {
		this.executedActions = executedActions;
	}
		
	/**
	 * Given an object, which could be either an entity or a proxy, get its primary key.
	 */
	public Serializable getPrimaryKeyForObject(Object object) {
		if (object == null)
			return null;
		
		Serializable fk = null;
		if (object instanceof HibernateProxy) {
			HibernateProxy proxy = (HibernateProxy)object;
			fk = proxy.getHibernateLazyInitializer().getIdentifier();
		}
		else {
			SessionFactory sessionFactory = session.getSessionFactory();
			ClassMetadata classMeta = sessionFactory.getClassMetadata(object.getClass());
			fk = classMeta.getIdentifier(object, (SessionImplementor)session);
		}
		return fk;
	}
	
	/**
	 * Get the use case name for this context.
	 */
	public String getUseCaseName() {
		return useCaseName;
	}

	/**
	 * Set the use case name for this context.
	 * @param useCaseName
	 */
	public void setUseCaseName(String useCaseName) {
		this.useCaseName = useCaseName;
	}
	
	/**
	 * Fire the given event with whoever should be notified.
	 */
	public static void fireEvent(LogicEvent evt) {
		GlobalLogicEventHandler.getGlobalLogicListenerHandler().fireEvent(evt);
	}

	////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("LogicTransactionContext: ");
		if (useCaseName != null)
			sb.append(" [use case : " + useCaseName + "]");
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicTransactionContext.java 952 2012-03-16 11:03:02Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 