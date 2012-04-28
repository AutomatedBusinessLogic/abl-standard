package com.autobizlogic.abl.engine.phase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hibernate.Transaction;

import com.autobizlogic.abl.rule.CommitConstraintRule;
import com.autobizlogic.abl.rule.ConstraintRule;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.ConstraintException;
import com.autobizlogic.abl.engine.ConstraintFailure;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.TransactionFailureSynchronization;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.event.GlobalLogicEventHandler;
import com.autobizlogic.abl.event.LogicAfterCommitEvent;
import com.autobizlogic.abl.event.LogicAfterCommitEvent.CommitFailure;
import com.autobizlogic.abl.text.LogicMessageFormatter;
import com.autobizlogic.abl.text.MessageName;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;


/**
 * Process Constraints
 */
public class Constraints extends LogicPhaseBase implements LogicPhase {

	public Constraints(LogicRunner aLogicRunner) {
		super(aLogicRunner);
	}
	
	@Override
	public void setLogicPhase() {
		logicRunner.logicPhase = LogicRunnerPhase.CONSTRAINTS;
	}

	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.RULES_ENGINE);

	/**
	 * execute non-commit constraints, combining exceptions into 1 (multiple constraints)
	 * <br><br>
	 * todo - invalid constraint attributes<br>
	 * todo - constraints role pruning<br>
	 * 
	 * @param logicRunner context for logic & domain objects
	 */
	@Override
	public void execute() {
		LogicGroup logicGroup = logicRunner.getLogicGroup();
		if (logicGroup == null)
			return;

		Set <ConstraintRule> constraints = logicGroup.getConstraints();
		List <ConstraintFailure> constraintFailures= new ArrayList<ConstraintFailure>();
		for (ConstraintRule eachConstraint: constraints) {
			if ( ! eachConstraint.verbIsRelevant(logicRunner))
				continue;
			ConstraintFailure failure = eachConstraint.executeConstraint(logicRunner);
			if (failure != null) {
				failure.setProblemClass(logicRunner.getCurrentDomainObject().getMetaEntity().getEntityName());
				failure.setProblemPk(logicRunner.getCurrentDomainObject().getPk());
				constraintFailures.add(failure);
			}
		}

		// No constraint failures? We're done
		if (constraintFailures.isEmpty())
			return;

		// There were constraint failures, package them up nicely, rollback the transaction and throw an exception
		StringBuffer constraintMsg = new StringBuffer();
		for (ConstraintFailure eachConstraintFailure: constraintFailures) {
			String msgLine = eachConstraintFailure.getConstraintMessage();
			constraintMsg.append("\n" + msgLine);
		}
		String msg = LogicMessageFormatter.getMessage(MessageName.rule_ConstraintFailure_prefix, constraintMsg.toString());

		ConstraintException ex = new ConstraintException(msg, constraintFailures);
		
		LogicAfterCommitEvent evt = new LogicAfterCommitEvent(logicRunner.getContext(), CommitFailure.CONSTRAINTFAILURE);
		GlobalLogicEventHandler.getGlobalLogicListenerHandler().fireEvent(evt);
		
		//aLogicRunner.getContext().getSession().getTransaction().rollback();
		
		// We want to guarantee that this transaction will not get committed, even if the exception we're about
		// to throw gets buried by some code somewhere. So we register a Synchronization with the transaction,
		// and if it gets to that, it will throw an exception before commit. Note that it will usually not get called,
		// only if our exception somehow gets buried.
		TransactionFailureSynchronization sync = new TransactionFailureSynchronization(ex);
		logicRunner.getContext().getSession().getTransaction().registerSynchronization(sync);

		throw ex;
	}
	
	/**
	 * Execute all CommitConstraints, based on the set of all the latest LogicRunners for each touched object.
	 */
	public static void executeAllCommitConstraints(Collection<LogicRunner> logicRunners) {
		
		List <ConstraintFailure> constraintFailures= new ArrayList<ConstraintFailure>();
		Transaction tx = null;
		LogicRunner failedLogicRunner = null;
		
		for (LogicRunner runner : logicRunners) {
			
			LogicGroup logicGroup = runner.getLogicGroup();
			if (logicGroup == null)
				continue;
			
			if (tx == null)
				tx = runner.getContext().getSession().getTransaction();
			Set<CommitConstraintRule> commitConstraints = logicGroup.getCommitConstraints();
			for (ConstraintRule constraint : commitConstraints) {
				if ( ! constraint.verbIsRelevant(runner))
					continue;
				if (log.isDebugEnabled())
					log.debug("CommitConstraint("+ constraint.getLogicMethodName() + ") invoking on", runner);

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

				ConstraintFailure failure = constraint.executeConstraint(runner);
				if (failure != null) {
					constraintFailures.add(failure);
					failedLogicRunner = runner;
				}
			}
		}

		// No constraint failures? We're done
		if (constraintFailures.isEmpty())
			return;

		// There were constraint failures, package them up nicely, poison the transaction and throw an exception
		StringBuffer constraintMsg = new StringBuffer();
		for (ConstraintFailure eachConstraintFailure: constraintFailures) {
			String msgLine = eachConstraintFailure.getConstraintMessage();
			constraintMsg.append("\n" + msgLine);
		}
		String msg = LogicMessageFormatter.getMessage(MessageName.rule_ConstraintFailure_prefix, constraintMsg.toString());
		
		ConstraintException ex = new ConstraintException(msg, constraintFailures);

		// Frameworks like Grails do not like to have transactions rolled back
		// by applications, so we no longer roll back, but rather just throw an exception, and make sure that
		// the transaction cannot be committed.
		if (tx != null) {
			LogicAfterCommitEvent evt = new LogicAfterCommitEvent(failedLogicRunner.getContext(), CommitFailure.CONSTRAINTFAILURE);
			GlobalLogicEventHandler.getGlobalLogicListenerHandler().fireEvent(evt);

			// We want to guarantee that this transaction will not get committed, even if the exception we're about
			// to throw gets buried by some code somewhere. So we register a Synchronization with the transaction,
			// and if it gets to that, it will throw an exception before commit. Note that it will usually not get called,
			// only if our exception somehow gets buried.
			TransactionFailureSynchronization sync = new TransactionFailureSynchronization(ex);
			tx.registerSynchronization(sync);
		}

		throw ex;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  Constraints.java 983 2012-03-23 01:39:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 