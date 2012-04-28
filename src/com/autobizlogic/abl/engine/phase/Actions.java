package com.autobizlogic.abl.engine.phase;

import java.io.Serializable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.rule.ActionRule;
import com.autobizlogic.abl.rule.CommitActionRule;
import com.autobizlogic.abl.rule.EarlyActionRule;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;


/**
 * Execute Action rules, including EarlyActionRule and CommitActionRule.
 */
public class Actions extends LogicPhaseBase implements LogicPhase {

	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	
	/**
	 * execute aLogicObject actions.
	 * 
	 * <br><br>
	 * Command Pattern - instantiate / run logic phase
	 * 
	 * @param aLogicRunner
	 */
	public Actions(LogicRunner aLogicRunner) {
		super(aLogicRunner);
	}
	
	public Actions(LogicRunner aLogicRunner, LogicRunnerPhase anExecutionState) {
		super(aLogicRunner);
		if (anExecutionState != LogicRunnerPhase.EARLY_ACTIONS)		  // might be more types later (not planned)
			throw new LogicException ("Actions detects unexpected phase");
		logicPhase = anExecutionState;			
	}
	
	@Override
	public void setLogicPhase() {
		logicPhase = LogicRunnerPhase.ACTIONS; 
	}

	

	/**
	 * Execute all action rules
	 * <ol>
	 * <li>no dependencies - always fire</li>
	 * <li>default recursion protection - only once per instance (unless explicitly request)</li>
	 * </ol>
	 * <p>
	 * 
	 * todo Action Design issue - given that we are not specifying condition,<br>
	 * user might want to return that their "action guard" prevented execution,<br>
	 * so that we execute the action in a future Logic Runner, if any.
	 * <p>
	 * 
	 * @param logicRunner context for logic & domain objects
	 */
	@Override
	public void execute() {
		
		if (logicPhase == LogicRunnerPhase.EARLY_ACTIONS) {
			executeEarlyActions();
			return;
		} 

		LogicGroup logicGroup = logicRunner.getLogicGroup();
		if (logicGroup == null)
			return;
		Set<ActionRule> actions = logicGroup.getActions();
		if (actions.size() == 0)
			return;
		
		LogicTransactionContext context = logicRunner.getContext();
		Map<Object, Set<ActionRule>> executedActions = context.getExecutedActions();
		Serializable pk = logicRunner.getCurrentDomainObject().getPk();
		String mapKey = logicRunner.getCurrentDomainObject().getEntityName() + "/" + pk;
		Set<ActionRule> beanExecutedActions = executedActions.get(mapKey);
		if (beanExecutedActions == null) {
			beanExecutedActions = new HashSet<ActionRule>();
			executedActions.put(mapKey, beanExecutedActions);
		}

		for (ActionRule eachAction: actions) {
			if ( ! eachAction.verbIsRelevant(logicRunner))
				continue;
			if (beanExecutedActions.contains(eachAction) && logicRunner.getVerb() != Verb.INSERT) {  // action done already?
				if (log.isDebugEnabled())
					log.debug("Action("+ eachAction.getLogicMethodName() + ") already fired, ", logicRunner);
			} else {
				if (log.isDebugEnabled())
					log.debug("Action("+ eachAction.getLogicMethodName() + ") invoking on ", logicRunner);
				beanExecutedActions.add(eachAction);
				eachAction.execute(logicRunner);
			} // action done already
		} // eachAction
	}
	
	/**
	 * Execute all the EarlyAction for the given LogicRunner.
	 */
	private void executeEarlyActions() {
		LogicGroup logicGroup = logicRunner.getLogicGroup();
		if (logicGroup == null)
			return;
		Set<EarlyActionRule> earlyActions = logicGroup.getEarlyActions();
		if (earlyActions.size() == 0)
			return;
		LogicTransactionContext context = logicRunner.getContext();
		Map<Object, Set<ActionRule>> executedActions = context.getExecutedActions();
		Serializable pk = logicRunner.getCurrentDomainObject().getPk();
		String mapKey = logicRunner.getCurrentDomainObject().getMetaEntity().getEntityName() + "/" + pk;
		Set<ActionRule> beanExecutedActions = executedActions.get(mapKey);
		if (beanExecutedActions == null) {
			beanExecutedActions = new HashSet<ActionRule>();
			executedActions.put(mapKey, beanExecutedActions);
		}

		for (ActionRule eachAction: earlyActions) {
			if ( ! eachAction.verbIsRelevant(logicRunner))
				continue;
			if (beanExecutedActions.contains(eachAction))  // action done already for this bean?
				continue;
			if (log.isDebugEnabled())
				log.debug("EarlyAction("+ eachAction.getLogicMethodName() + ") invoking on", logicRunner);
			beanExecutedActions.add(eachAction);
			eachAction.execute(logicRunner);
		}
	}
	
	/**
	 * Execute all CommitActions, based on the set of all the latest LogicRunners for each touched object.
	 * Note that all bean objects are made read-only before the action is called.
	 */
	public static void executeAllCommitActions(Collection<LogicRunner> logicRunners) {
		
		for (LogicRunner runner : logicRunners) {
			
			LogicGroup logicGroup = runner.getLogicGroup();
			if (logicGroup == null)
				continue;
			Set<CommitActionRule> commitActions = logicGroup.getCommitActions();
			for (CommitActionRule action : commitActions) {
				if ( ! action.verbIsRelevant(runner))
					continue;
				if (log.isDebugEnabled())
					log.debug("EarlyAction("+ action.getLogicMethodName() + ") invoking on", runner);
				
				// Set up the LogicContext, which may have been shared by several LogicRunners
				PersistentBean currentBean = runner.getCurrentDomainObject();
				if (currentBean != null)
					runner.getLogicContext().setCurrentState(currentBean);
				else
					runner.getLogicContext().setCurrentState(runner.getCurrentDomainObject());
				
				if (runner.getPriorDomainObject() != null) {
					PersistentBean priorBean = runner.getPriorDomainObject();
					if (priorBean != null)
						runner.getLogicContext().setOldState(priorBean);
					else
						runner.getLogicContext().setOldState(runner.getPriorDomainObject());
				}
				runner.getLogicContext().setLogicRunner(runner);

				action.execute(runner);
			}
		}
	}
	

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  Actions.java 983 2012-03-23 01:39:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 