package com.autobizlogic.abl.logic;

import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.logic.LogicSource;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.engine.LogicRunner.LogicProcessingState;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.BeanMap;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;
import com.autobizlogic.abl.util.NodalPathUtil;

/**
 * Override these to instantiate substitution classes.. with care.
 */

public class BusinessLogicFactoryImpl implements BusinessLogicFactory {
	
	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	
	public BusinessLogicFactoryImpl() {
		super();
	}
	
	/**
	 * Factory, providing flexibility/extensibility for associating a <br>
	 * Business Logic Component with a domain (pojo) class.
	 * <p>
	 * 
	 * The default factory computes the Business Logic Component from the domain class.
	 * <blockquote>
	 *  For domain objects in <code>com.app.Customer</code>, <br>
	 *  the Business Logic Component is <code>com.app.Customer<em>Logic</em></code>
	 * </blockquote>
	 * You can use the factory to override this on a general or per-instance basis.<br>
	 * For example, you might have a single domain class PurchaseOrder, <br>
	 * and desire different logic for retail vs wholesale orders.  You can
	 * <ol>
	 * <li>Define a common shared Business Logic Component <code>PurchaseOrderLogicBase</code>
	 * <li>Extend this with type-specific logic with <code>PurchaseOrderLogicRetail</code> <br>
	 * and <code>PurchaseOrderLogicWholesale</code>
	 * <li>Implement this factory to choose between the latter two, based on <code>aDomainObject.attribute</code>
	 * </ol>
	 * 
	 * @param aDomainObject Hibernate POJO instance (e.g, a PurchaseOrder bean)
	 * @return instance of logic class (e.g., PurchaseOrderLogic) for aDomainObject (or null)
	 */
	@Override
	public Object createLogicObjectForDomainObject(PersistentBean objectState) {
		
		RuleManager ruleManager = RuleManager.getInstance(objectState.getMetaEntity().getMetaModel());
		LogicGroup logicGroup = ruleManager.getLogicGroupForEntity(objectState.getMetaEntity());
		if (logicGroup == null)
			return null;

		String logicClassName = logicGroup.getLogicClassName();
		
		Class<?> logicClass = null;
		Object logicObject = null;
		try {
			logicClass = ClassLoaderManager.getInstance().getLogicClassFromName(logicClassName);
		} catch (Exception e) {
			throw new LogicException("Unable to load logic class " + logicClassName, e);
		}
		
		try {
			logicObject = logicClass.newInstance();
		} catch (Exception e) {
			throw new LogicException("Unable to instantiate Logic instance (e.g., " +
					"missing dependson() - check annotations/errors) - class " + logicClassName, e);
		}
		return logicObject;
	}
	
	
	/**
	 * 
	 * @param aLogicTransactionContext
	 * @param currentObjectState Current state of the object
	 * @param priorObjectState Prior state of the object, can be null for inserts
	 * @param aVerb - insert, update or delete
	 * @param aLogicSource
	 * @param aCallingLogicRunner
	 * @param aCallingRoleMeta
	 * @return returns instance that can execute the rules for currentObjectState
	 */
	@Override
	public  LogicRunner createLogicRunner(LogicTransactionContext aLogicTransactionContext, 
			PersistentBean currentState, PersistentBean priorState, Verb aVerb, 
			LogicSource aLogicSource, LogicRunner aCallingLogicRunner, MetaRole callingRole) {
		
		LogicRunner runner = aLogicTransactionContext.findLogicRunner(currentState);
		if (runner != null && 
				runner.getLogicProcessingState() != LogicProcessingState.COMPLETED &&
				runner.getExecutionState() == LogicRunnerPhase.ACTIONS) {
			priorState = currentState.duplicate();
			runner = new LogicRunner(aLogicTransactionContext, currentState, priorState, 
					aVerb, aLogicSource, aCallingLogicRunner, callingRole);
			aLogicTransactionContext.addObjectToProcess(runner);
			aLogicTransactionContext.registerLogicRunner(runner);
		}
		if (runner == null || 
				runner.getLogicProcessingState() == LogicProcessingState.COMPLETED ||
						runner.getExecutionState() != LogicRunnerPhase.EARLY_ACTIONS) {
			runner = new LogicRunner(aLogicTransactionContext, currentState, priorState, 
				aVerb, aLogicSource, aCallingLogicRunner, callingRole);
			aLogicTransactionContext.registerLogicRunner(runner);
		}
		else {
			log.debug("Existing LogicRunner found -- using it");
		}
		return runner;
	}
	
	@Override
	public LogicRunner getLogicRunner(LogicTransactionContext aLogicTransactionContext, PersistentBean currentState, 
			PersistentBean priorState, Verb aVerb, LogicSource aLogicSource, LogicRunner aCallingLogicRunner, MetaRole callingRole) {

		LogicRunner runner = aLogicTransactionContext.findLogicRunner(currentState);
		
		// If there is already a LogicRunner for this object, and it's already executing actions,
		// we create a new LogicRunner, queue it up, and return null to indicate that no
		// further processing is required.
		if (runner != null && 
				runner.getLogicProcessingState() != LogicProcessingState.COMPLETED &&
				runner.getExecutionState() == LogicRunnerPhase.ACTIONS) {
			if (aLogicSource != LogicSource.LOGIC)
				priorState = currentState.duplicate();
			// It's possible that someone else has already queued up a new LogicRunner for this object,
			// in which case we're done.
			LogicRunner lastLogicRunner = aLogicTransactionContext.findNewestLogicRunner(currentState);
			if (lastLogicRunner.getExecutionState() == LogicRunnerPhase.NOT_STARTED) {
				log.debug("Found not yet started LogicRunner for this object -- nothing to do");
				lastLogicRunner.setPriorDomainObject(priorState);
				return null;
			}
			runner = new LogicRunner(aLogicTransactionContext, currentState, priorState, 
					aVerb, aLogicSource, null, callingRole);
			aLogicTransactionContext.addObjectToProcess(runner);
			aLogicTransactionContext.registerLogicRunner(runner);
			log.debug("Found existing LogicRunner in ACTION -- creating and queueing new LogicRunner ");
			return null;
		}
		
		// If there is already a LogicRunner for this object, and it's executing early actions,
		// we're just going to let it execute in its own time, and return null to signal that
		// no further processing is required.
		if (runner != null &&
				runner.getLogicProcessingState() != LogicProcessingState.COMPLETED &&
				runner.getExecutionState() == LogicRunnerPhase.EARLY_ACTIONS) {
			log.debug("Found existing LogicRunner in EARLY_ACTION -- no further action required");
			return null;
		}

		return createLogicRunner(aLogicTransactionContext, currentState, priorState, aVerb,
				aLogicSource, aCallingLogicRunner, callingRole);
	}
	
	/**
	 * factory
	 * @return new (empty) LogicContext
	 * @see com.autobizlogic.abl.context.LogicContext
	 */
	@Override
	public  LogicContext createLogicContext() {
		return new LogicContext();
	}


	/**
	 * 
	 * @param aLogicContext
	 * @return useCaseName (either explicit per attr named useCase[Name], or implicit as Object_verb)
	 */
	@Override
	public String computeUseCaseName(LogicContext aLogicContext) {
		LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
		String useCase = null;
		Object bean =  aLogicContext.getCurrentState();
		
		BeanMap beanMap = new BeanMap(bean);		// look for explicit useCase
		//@SuppressWarnings("unchecked")
		useCase = (String) beanMap.get("useCaseName");
		if (useCase == null)
			useCase = (String) beanMap.get("useCase");
		
		if (useCase == null) {
			String entityName = LogicContext.getEntityNameForObject(bean);
			entityName = NodalPathUtil.getNodalPathLastName(entityName);
			String verb = "save";
			if (aLogicContext.getVerb() == Verb.UPDATE)
				verb = "update";
			else if (aLogicContext.getVerb() == Verb.DELETE)
				verb = "delete";
			useCase = entityName + "_" + verb;
		}

		if (_logger.isInfoEnabled())  _logger.info ("\n\n\n#BEGIN Use Case: " + useCase + "   **********\n");
		return useCase;
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
 