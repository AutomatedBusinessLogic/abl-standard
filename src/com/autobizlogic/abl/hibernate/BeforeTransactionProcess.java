package com.autobizlogic.abl.hibernate;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.action.BeforeTransactionCompletionProcess;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.impl.SessionImpl;

import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.hibernate.LogicEventListener.QueuedEventPhase;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.phase.Actions;
import com.autobizlogic.abl.engine.phase.Constraints;
import com.autobizlogic.abl.event.GlobalLogicEventHandler;
import com.autobizlogic.abl.event.LogicAfterCommitEvent;
import com.autobizlogic.abl.event.LogicBeforeCommitEvent;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.session.LogicTransactionManager;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * This class gets registered with Hibernate in TransactionFactoryProxy.
 * <p/>
 * It gets called before a transaction gets committed, and processes all the objects that have been accumulated
 * in the LogicTransactionContext's objectsToProcess by LogicEventListener.
 * <p/>
 * For a possible alternative to this approach, see:
 * http://opensource.atlassian.com/projects/hibernate/secure/attachment/15486/HibernateWorkaroundHH2763.java
 * 
 * <h5>Logic Phase</h5>
 * <code>LogicRunners</code> <em>forward chain</em> to each other for cascade / adjust processing.<br>
 * Such invocations are by <em>direct invocation</em>, not via updates / event handlers - 
 * no Hibernate events fire during this phase.

 * <h5>Commit Phase</h5>
 * <code>doBeforeTransactionCompletion</code> processes all entries in LogicTransactionContext.commitLogic
 * These are built as Commit Constraints/Actions processed during the Logic Phase.
 * 
 * <h5>Flush Phase</h5>
 * <code>doBeforeTransactionCompletion</code> issues a flush, having set <code>masterFlushIsInProgress</code> 
 * so LogicRunners skip Logic Component processing</li>

 */
public class BeforeTransactionProcess implements BeforeTransactionCompletionProcess {

	private static final LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	
	@Override
	public void doBeforeTransactionCompletion(SessionImplementor session) {
		long startTime = System.nanoTime();
		SessionImpl sessionImpl = (SessionImpl)session;
		Transaction tx = sessionImpl.getTransaction();
		LogicTransactionContext context = LogicTransactionManager.getCurrentLogicTransactionContextForTransaction(tx, (Session)session);	
		if (context.getQueuedEventPhase() == QueuedEventPhase.SUBMIT) {
			_logger.info("********** Business Logic Phase starting - LogicRunners will now invoke Logic Components (Hibernate doBeforeTransactionCompletion)");
		}
		context.setQueuedEventPhase(QueuedEventPhase.LOGIC);
		
		// If we're using dynamic logic, this is the time to refresh anything that needs to be
		ClassLoaderManager.getInstance().checkForClassUpdate();

		List<LogicRunner> objectsToProcess = context.getObjectsToProcess();
		
		// Now iterate over all the LogicRunners accumulated during the transaction until they're
		// all gone.
		int numIterations = 0;
		
		while (! objectsToProcess.isEmpty()) {
			numIterations++;
			if (numIterations > 10000)
				throw new RuntimeException("Too many iterations in logic execution loop");
			
			Set<LogicRunner> processedObjects = new HashSet<LogicRunner>();
			for (LogicRunner eachRunner : objectsToProcess) {  // invoke rules
				
				if (eachRunner.getVerb() == Verb.UPDATE)
					eachRunner.update();
				else if (eachRunner.getVerb() == Verb.INSERT) 
					eachRunner.insert();
				else if (eachRunner.getVerb() == Verb.DELETE)
					eachRunner.delete();
				else {
					throw new LogicException("Unexpected Verb from LogicRunner: " + eachRunner.toString());
				}
				processedObjects.add(eachRunner);
			}

			objectsToProcess.removeAll(processedObjects);
			
			session.flush();  // can re-fill objectsToProcess, maybe loop
			_logger.info("********** Flush Phase completed (Hibernate doBeforeTransactionCompletion)");
		}
		
		LogicBeforeCommitEvent beforeCommitEvent = new LogicBeforeCommitEvent(context);
		GlobalLogicEventHandler.getGlobalLogicListenerHandler().fireEvent(beforeCommitEvent);
		
		Set<LogicRunner> allRunners = context.getAllLogicRunners();
		
		// Invoke commit-time actions and constraints.
		Actions.executeAllCommitActions(allRunners);
		Constraints.executeAllCommitConstraints(allRunners);
		
		LogicAfterCommitEvent evt = new LogicAfterCommitEvent(context);
		evt.setExecutionTime(System.nanoTime() - startTime);
		GlobalLogicEventHandler.getGlobalLogicListenerHandler().fireEvent(evt);
		
		// Now finalize the transaction summary
		context.getTransactionSummary().setCommitTimestamp(new Timestamp(System.currentTimeMillis()));
		context.getTransactionSummary().setSessionId("Session" + session.hashCode());
		context.getTransactionSummary().setTransactionId("" + tx.hashCode());
		GlobalLogicEventHandler.getGlobalTransactionSummaryListenerHandler().publishSummary(context.getTransactionSummary());

		if (_logger.isInfoEnabled()) _logger.info("End (current iteration): doBeforeTransactionCompleted - " + numIterations + " iterations");
	}
	
	@SuppressWarnings("unused")
	private final static long serialVersionUID = 1996220184856674839L;
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  BeforeTransactionProcess.java 964 2012-03-20 03:46:11Z val@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 