package com.autobizlogic.abl.engine;

import java.util.List;

import com.autobizlogic.abl.data.BeanComparison;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.ProxyFactory;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanCopy;
import com.autobizlogic.abl.engine.phase.Actions;
import com.autobizlogic.abl.engine.phase.AdjustAllParents;
import com.autobizlogic.abl.engine.phase.CascadeParentReferences;
import com.autobizlogic.abl.engine.phase.Constraints;
import com.autobizlogic.abl.engine.phase.DefaultsAndAlterability;
import com.autobizlogic.abl.engine.phase.FormulasAndParentChecks;
import com.autobizlogic.abl.event.GlobalLogicEventHandler;
import com.autobizlogic.abl.event.LogicRunnerEvent;
import com.autobizlogic.abl.event.LogicRunnerEvent.LogicRunnerEventType;
import com.autobizlogic.abl.hibernate.LogicEventListener.QueuedEventPhase;
import com.autobizlogic.abl.logic.BusinessLogicFactory;
import com.autobizlogic.abl.logic.BusinessLogicFactoryManager;
import com.autobizlogic.abl.logic.EntityProcessorFactory;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.logic.LogicSource;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;
import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.ObjectUtil;
import com.autobizlogic.abl.util.StringUtil;

/**
 * Runs the business logic for a specified domain object + corresponding BusinessLogicComponent
 * One instance per domain/businessLogic execution instance,<br>
 * instances created for each changed domain object,<br>
 * state includes BusinessLogicComponent, context, and old/new domain object,<br>
 * behavior is insert, update and delete.
 * <p>
 * 
 * Invoked by LogicListener, via Hibernate Events.
 * 
 * Relies upon the Dependency Analyzer, which has built ordered rule instances
 * for a Domain Object, available through a Logic Group.
 * 
 * LogicRunner instances are often 1:1 with a domain bean, but that is not required.<br>
 * For example,<ol>
 * <li>An PuchaseOrder might be made-ready</li>
 * <li>Which cascades to lineitems</li>
 * <li>Each of which adjusts Part.TotalQtyShipped</li>
 * <li>Which recomputes Product.needsReorder</li>
 * <li>Which propagates to Lineitems</li>
 * <li>Which adjusts the original PurchaseOrder - a new LogicRunner</li>
 * </ol>
 * 
 * @see com.autobizlogic.abl.businesslogicadapter.persistence.hibernate.LogicEventListener
 */
public class LogicRunner { 

	private Object logicObject;					// see ctor

	/**
	 * The current state of the domain object
	 */
	private PersistentBean currentState;

	/**
	 * The previous state of the domain object. This will not be set
	 * for insert events, obviously.
	 */
	private PersistentBean priorState;

	/**
	 * LogicTransactionContext - system context
	 */
	protected LogicTransactionContext context;

	private LogicGroup logicGroup;
	private Verb verb;


	private List <MetaRole> adjustedRolesDB;			// for debug
	private List<MetaRole> cascadeRolesDB;


	/**
	 * The initiator for this LogicRunner.
	 */
	protected LogicSource logicSource = LogicSource.USER; 

	/**
	 * who called this LogicRunner?  and along what role??
	 */
	protected LogicRunner callingLogicRunner = null;
	protected MetaRole callingRole = null;
	protected int logicNestLevel = 0;

	/**
	 * User-visible context (placed into Logic Component Classes)
	 */
	protected LogicContext logicContext = null;

	public enum LogicProcessingState {
		QUEUED, 
		RUNNING, 
		COMPLETED
	}
	
	/**
	 * The current state of this LogicRunner. It will be QUEUED as long as the LogicRunner is
	 * not running, then it will switch to RUNNING. Once the LogicRunner has completed, the state
	 * will switch to COMPLETED.
	 */
	protected LogicProcessingState logicProcessingState = LogicProcessingState.QUEUED;
	
	/**
	 * The possible execution states for a LogicRunner
	 */
	public enum LogicRunnerPhase {
		/**
		 * This LogicRunner has not been executed yet
		 */
		NOT_STARTED,
		
		/**
		 * Currently executing early actions
		 */
		EARLY_ACTIONS,
		
		/**
		 * Currently executing the bulk of the logic
		 */
		LOGIC,
		
		/**
		 * Currently executing constraints
		 */
		CONSTRAINTS,
		
		/**
		 * Currently executing actions
		 */
		ACTIONS,
		
		/**
		 * Currently cascading to parents
		 */
		CASCADE,
		
		/**
		 * This LogicRunner is finished
		 */
		FINISHED
	}
	
	public LogicRunnerPhase logicPhase = LogicRunnerPhase.NOT_STARTED;

	private static final LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);


	/**
	 * Create a new LogicRunner.
	 * @param aContext LogicTransactionContext
	 * @param aCurrentObjectState current state of the domain object
	 * @param aPriorObjectState prior state of the domain object. Can be null for inserts.
	 * @param aVerb - Verb
	 * @param aLogicSource - LogicSource
	 * @param aCallingLogicRunner - forward chains from here
	 * @param aCallingRoleMeta - forward chain RoleMeta
	 */
	public LogicRunner(LogicTransactionContext aContext, PersistentBean currentState, PersistentBean priorState, 
			Verb aVerb, LogicSource aLogicSource, LogicRunner aCallingLogicRunner, MetaRole aCallingRole) {
		this.context = aContext;
		this.currentState = currentState;
		if (currentState instanceof HibPersistentBeanCopy)
			throw new RuntimeException("Current state should never be a read-only copy");

		this.priorState = priorState;
		this.verb = aVerb;
		this.logicSource = aLogicSource;
		this.callingLogicRunner = aCallingLogicRunner;
		this.callingRole = aCallingRole;

		BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();

		if (aCallingLogicRunner != null) {
			logicNestLevel = aCallingLogicRunner.getLogicNestLevel() + 1;
			logicContext = aCallingLogicRunner.logicContext;
		} else {
			logicContext = businessLogicFactory.createLogicContext();
			logicContext.setSession(aContext.getSession());
		}
		logicContext.setLogicNestLevel(logicNestLevel);
		logicContext.setVerb(aVerb);

		logicContext.setCurrentState(currentState);
		logicContext.setOldState(priorState);

		logicContext.setInitialVerb(aVerb);
		logicContext.setLogicRunner(this);

		if ( aContext.getUseCaseName() == null  ) 
			aContext.setUseCaseName(businessLogicFactory.computeUseCaseName(logicContext));

		if (logicNestLevel == 0)	// DOCME - For only fwd chain objects, we need to save them
			aContext.getUserSubmittedObjects().add(currentState);
	}


	/**
	 * called by adapter / fwdChain to execute rules in response to domain object update
	 * <ol>
	 * 
	 * <li>exec formulas (per dependence), acquiring parent references as necessary</li>
	 * <li>constraints (which may exit with exception</li>
	 * <li>cascade changes from attributes references by children</li>
	 * <li>adjust parent aggregates</li> 
	 * <li>if FwdChain (not user submitted), issue <code>Hibernate update</code></li>
	 * </ol>
	 */
	public void update() {
		if (context.getQueuedEventPhase() != QueuedEventPhase.LOGIC)
			throw new LogicException("System Error - unexpected QueuedEventPhase");
		
		EntityProcessorFactory.preProcess(Verb.UPDATE, getCurrentDomainObject());

		if (_logger.isDebugEnabled()) _logger.debug ("##UPDATE BEGIN on", this); 
		setLogicProcessingState(LogicProcessingState.RUNNING);
		raiseLogicRunnerEvent(LogicRunnerEventType.BEGINUPDATE, 0);
		long startTime = System.nanoTime();
		LogicContext savedLogicContext = getLogicContext().saveLogicContext();
		new Actions(this, LogicRunnerPhase.EARLY_ACTIONS).execute();
		new FormulasAndParentChecks(this).execute();
		new Constraints(this).execute();
		new Actions(this).execute();
		new CascadeParentReferences(this).execute();  	// presumably replicates may affect sums, for constraints
		new AdjustAllParents(this).execute();
		logicPhase = LogicRunnerPhase.FINISHED;
		if ( ! context.getUserSubmittedObjects().contains(currentState) ) {
			if (currentState.getMetaEntity().isPojo())
				context.getSession().update(currentState.getBean());
			else if (currentState.getMetaEntity().isMap())
				context.getSession().update(currentState.getMetaEntity().getEntityName(), currentState.getMap());
			if (_logger.isDebugEnabled())  _logger.debug (LogicLogger.logicRunnerInfo("#UPDATE Forward Chain object saved:  ", this));     
		}
		getLogicContext().restoreLogicContext(savedLogicContext);
		setLogicProcessingState(LogicProcessingState.COMPLETED);
		if (_logger.isDebugEnabled())  _logger.debug ("##UPDATE END on", this);
		raiseLogicRunnerEvent(LogicRunnerEventType.END, System.nanoTime() - startTime);

		EntityProcessorFactory.postProcess(Verb.UPDATE, getCurrentDomainObject());

		logicNestLevel = logicNestLevel - 1;
	}



	/**
	 * called by adapter / fwdChain to execute rules in response to domain object insert
	 * <ol>
	 * 
	 * <li>exec formulas (per dependence), acquiring parent references as necessary</li>
	 * <li>constraints (which may exit with exception</li>
	 * <li>adjust parent aggregates</li> 
	 * </ol>
	 */
	public void insert() {
		if (context.getQueuedEventPhase() != QueuedEventPhase.LOGIC)
			throw new LogicException("System Error - unexpected QueuedEventPhase");

		EntityProcessorFactory.preProcess(Verb.INSERT, getCurrentDomainObject());

		if (_logger.isDebugEnabled())  _logger.debug ("##INSERT BEGIN on", this);
		setLogicProcessingState(LogicProcessingState.RUNNING);
		raiseLogicRunnerEvent(LogicRunnerEventType.BEGININSERT, 0);
		long startTime = System.nanoTime();
		LogicContext savedLogicContext = getLogicContext().saveLogicContext();
		new DefaultsAndAlterability(this).execute();
		new Actions(this, LogicRunnerPhase.EARLY_ACTIONS).execute();
		new FormulasAndParentChecks(this).execute();
		new Constraints(this).execute();
		new Actions(this).execute();
		new AdjustAllParents(this).execute();
		logicPhase = LogicRunnerPhase.FINISHED;
		getLogicContext().restoreLogicContext(savedLogicContext);
		setLogicProcessingState(LogicProcessingState.COMPLETED);
		if (_logger.isDebugEnabled())  _logger.debug ("##INSERT END on", this);
		raiseLogicRunnerEvent(LogicRunnerEventType.END, System.nanoTime() - startTime);

		EntityProcessorFactory.postProcess(Verb.INSERT, getCurrentDomainObject());

		logicNestLevel = logicNestLevel - 1;
	}


	/**
	 * called by adapter / fwdChain to execute rules in response to domain object delete
	 * <ol>
	 * 
	 * <li>exec formulas (per dependence), acquiring parent references as necessary</li>
	 * <li>constraints (which may exit with exception</li>
	 * <li>cascade changes from attributes references by children</li>
	 * <li>adjust parent aggregates</li> 
	 * </ol>
	 */
	public void delete() {
		if (context.getQueuedEventPhase() != QueuedEventPhase.LOGIC)
			throw new LogicException("System Error - unexpected QueuedEventPhase");

		EntityProcessorFactory.preProcess(Verb.DELETE, getCurrentDomainObject());

		if (_logger.isDebugEnabled())  _logger.debug ("##DELETE BEGIN on", this);
		setLogicProcessingState(LogicProcessingState.RUNNING);
		raiseLogicRunnerEvent(LogicRunnerEventType.BEGINDELETE, 0);
		long startTime = System.nanoTime();
		LogicContext savedLogicContext = getLogicContext().saveLogicContext();
		new Actions(this, LogicRunnerPhase.EARLY_ACTIONS).execute();
		new FormulasAndParentChecks(this).execute();
		new Constraints(this).execute();
		// CascadeParentReferences.execute(this);  // delete is reactive (wait for events) not proactive
		new Actions(this).execute();
		new AdjustAllParents(this).execute();								// unless cascade deleting me
		logicPhase = LogicRunnerPhase.FINISHED;
		getLogicContext().restoreLogicContext(savedLogicContext);
		setLogicProcessingState(LogicProcessingState.COMPLETED);
		if (_logger.isDebugEnabled())  _logger.debug ("##DELETE END on", this);
		raiseLogicRunnerEvent(LogicRunnerEventType.END, System.nanoTime() - startTime);

		EntityProcessorFactory.postProcess(Verb.DELETE, getCurrentDomainObject());

		logicNestLevel = logicNestLevel - 1;
	}


	public LogicTransactionContext getContext() {
		return context;
	}

	/**
	 * Get the Verb for this LogicRunner: the type of activity that it is taking care of.
	 */
	public Verb getVerb() {
		return verb;
	}

	public void setVerb(Verb aVerb) {
		verb = aVerb;
	}

	/**
	 * Get the logic object for this LogicRunner: the instance of the logic class that
	 * is currently in use (e.g. CustomerLogic).
	 */
	public Object getLogicObject() {
		if (logicObject == null) {
			BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();
			logicObject = businessLogicFactory.createLogicObjectForDomainObject(currentState);
			if (logicObject != null && getLogicGroup() != null) {
				String beanPropertyName = getLogicGroup().getCurrentBeanFieldName();
				if (beanPropertyName != null)
					BeanUtil.setBeanPropertyToPersistentBean(logicObject, beanPropertyName, currentState);

				String oldBeanProperty = getLogicGroup().getOldBeanFieldName();
				if (oldBeanProperty != null && priorState != null) {
					if (priorState.getMetaEntity().isPojo()) {
						Object priorStateProxy = ProxyFactory.getProxyForEntity(priorState);
						BeanUtil.setBeanProperty(logicObject, oldBeanProperty, priorStateProxy);
					}
					else {
						BeanUtil.setBeanProperty(logicObject, oldBeanProperty, priorState);
					}
				}

				String contextFieldName = getLogicGroup().getContextFieldName();
				if (contextFieldName != null)
					BeanUtil.setBeanProperty(logicObject, contextFieldName, logicContext);
			}
		}
		return logicObject;
	}

	/**
	 * Get the persistent bean that this LogicRunner is centered on.
	 */
	public PersistentBean getCurrentDomainObject() {
		return currentState;
	}

	public PersistentBean getPriorDomainObject() {
		return priorState;
	}
	
	public void setPriorDomainObject(PersistentBean pbean) {
		this.priorState = pbean;
		logicContext.setOldState(pbean);
	}

	/**
	 * Get the LogicGroup for this LogicRunner.
	 */
	public LogicGroup getLogicGroup() {
		if (logicGroup == null) {
			logicGroup = RuleManager.getInstance(context.getMetaModel()).getLogicGroupForEntity(currentState.getMetaEntity());
		}
		return logicGroup;
	}

	/**
	 * Get the logic source for this LogicRunner, ie the reason why it was created
	 * in the first place.
	 */
	public LogicSource getLogicSource() {
		return logicSource;
	}

	public void setLogicSource(LogicSource logicSource) {
		this.logicSource = logicSource;
	}

	public LogicRunner getCallingLogicRunner() {
		return callingLogicRunner;
	}

	public void setCallingLogicRunner(LogicRunner callingLogicRunner) {
		this.callingLogicRunner = callingLogicRunner;
	}

	/**
	 * Get the role along which this LogicRunner was created. By definition, this only
	 * applies to LogicRunners that were created by forward chaining.
	 * @return Null if this LogicRunner was not created from cascade along a role,
	 * otherwise the role.
	 */
	public MetaRole getCallingRole() {
		return callingRole;
	}

	public void setCallingRoleMeta(MetaRole callingRole) {
		this.callingRole = callingRole;
	}

	public int getLogicNestLevel() {
		return logicNestLevel;
	}

	public void setLogicNestLevel(int logicNestLevel) {
		this.logicNestLevel = logicNestLevel;
	}

	/**
	 * Get the LogicContext for this LogicRunner.
	 */
	public LogicContext getLogicContext() {
		return logicContext;
	}

	/**
	 * Get the logicProcessingState of this LogicRunner.
	 */
	public LogicProcessingState getLogicProcessingState() {
		return logicProcessingState;
	}

	/**
	 * Set the logicProcessingState of this LogicRunner.
	 */
	public void setLogicProcessingState(LogicProcessingState aLogicProcessingState) {
		logicProcessingState = aLogicProcessingState;
	}
	
	/**
	 * Find out at which stage of execution this logic runner is.
	 */
	public LogicRunnerPhase getExecutionState() {
		return logicPhase;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Notify the event system of an event for this LogicRunner.
	 * @param aLogicRunnerEventType The type of event
	 * @param execTime The amount of time this event took, in nanosecs.
	 */
	public void raiseLogicRunnerEvent(LogicRunnerEventType aLogicRunnerEventType, long execTime) {
		LogicRunnerEvent evt = new LogicRunnerEvent(this.getContext(), this.getLogicContext(), aLogicRunnerEventType);
		evt.setExecutionTime(execTime);
		GlobalLogicEventHandler.getGlobalLogicListenerHandler().fireEvent(evt);
	}



	private static boolean verboseDebug = false;

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer(toString(null));
		if (verboseDebug) {
			sb.append("\n");
			sb.append(getCurrentDomainObject().toString());
			sb.append("\n\n adjustments: ");
			sb.append(StringUtil.collectionToString(getAdjustedRolesDB()));
			sb.append("\n cascades: ");
			sb.append(StringUtil.collectionToString(getCascadeRolesDB()));
			sb.append("\n*******************");
		}
		return sb.toString();
	}
	
	/**
	 * 
	 * @param aMsg - null means no message, and no indenting
	 * @return entire string (e.g., for info/debug(aMsg, aLogicRunner)
	 */
	public String toString(String aMsg) {
		String msg = "";
		if (aMsg != null)
			msg = nestIdents();
		
		msg += classAndKey();
		if (aMsg == null)
			msg = msg.substring(1);
		else
			msg += aMsg;
		
		if (msg.endsWith(": ") ) 
			msg = msg.substring(0, msg.length()-2);
		if (msg.endsWith(":") || msg.endsWith(" ")) 
			msg = msg.substring(0, msg.length()-1);
		String beanNameValue = BeanComparison.comparePersistentBeans(getCurrentDomainObject(), 
				getPriorDomainObject(), this.getLogicContext().getSession());
		if (beanNameValue.startsWith("Bean ")) {
			int valueStartX = beanNameValue.indexOf('[');
			// String beanName = NodalPathUtil.getNodalPathLastName(beanNameValue.substring(4, valueStartX));
			valueStartX = beanNameValue.indexOf('=');
			beanNameValue = beanNameValue.substring(valueStartX-1);
		}
		msg += beanNameValue;
		msg += ", logicProcessingState: " + getLogicProcessingState() ;
		msg += ", verb: "+ getVerb();
		msg += ", executionState: " + getExecutionState();
		msg += ", logicNestLevel: " + getLogicNestLevel() ;
		return msg;
	}

	/**
	 * Get a short title for this LogicRunner, e.g. [Purchaseorder[1] UPDATE]
	 */
	public String classAndKey() {
		String msg = "";
		MetaRole callingRoleMeta = getCallingRole();
		String realClass = getCurrentDomainObject().getEntityName();
		realClass = NodalPathUtil.getNodalPathLastName(realClass);
		realClass += "[" + ObjectUtil.safeToString(getCurrentDomainObject().getPk()) + "]";
		if (getLogicSource() == LogicSource.USER) {
			msg += " [" +  realClass ;
			if (callingRoleMeta != null) {		// perhaps improve to use role direction
				msg += " via " + callingRoleMeta.getRoleName();
			}
			msg += " " + verb;
		} else {
			msg += " [" + realClass;
			msg += " " + getLogicSource();
			if (callingRoleMeta != null) {		// perhaps improve to use role direction
				msg += " via " + callingRoleMeta.getRoleName();
			}
		}
		return msg + "] ";

	}

	private  String nestIdents() {
		StringBuffer msg = new StringBuffer();
		for (int i = getLogicNestLevel(); i > 0; i--)
			msg.append("  | ");
		return msg.toString();
	}

	public List <MetaRole> getAdjustedRolesDB() {
		return adjustedRolesDB;
	}


	public void setAdjustedRolesDB(List <MetaRole> adjustedRolesDB) {
		this.adjustedRolesDB = adjustedRolesDB;
	}



	public List<MetaRole> getCascadeRolesDB() {
		return cascadeRolesDB;
	}


	public void setCascadeRolesDB(List<MetaRole> cascadeRolesDB) {
		this.cascadeRolesDB = cascadeRolesDB;
	}



	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicRunner.java 1248 2012-04-23 23:28:58Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 