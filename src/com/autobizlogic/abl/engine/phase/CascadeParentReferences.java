package com.autobizlogic.abl.engine.phase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hibernate.collection.PersistentCollection;
import org.hibernate.engine.PersistenceContext;

import com.autobizlogic.abl.logic.BusinessLogicFactoryManager;
import com.autobizlogic.abl.logic.LogicSource;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.engine.LogicRunner.LogicProcessingState;
import com.autobizlogic.abl.logic.BusinessLogicFactory;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.event.LogicRunnerEvent.LogicRunnerEventType;
import com.autobizlogic.abl.hibernate.HibernateSessionUtil;
import com.autobizlogic.abl.hibernate.HibernateUtil;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.metadata.hibernate.HibMetaEntity;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;
import com.autobizlogic.abl.util.ObjectUtil;

/**
 * For each children role of this parent:<ol>
 * <li> cascade changes to ref'd (parent) attrs, if any</li>
 * <li> but, do <em>not</em> push cascade delete - let Hibernate drive</ol>
 * </ol>
 * When triggering cascades, provide forwardChain info to child,<br>
 * used for role pruning.
 * 
 */
public class CascadeParentReferences extends LogicPhaseBase implements LogicPhase {

	private static final LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	private static final LogicLogger _logSys = LogicLogger.getLogger(LoggerName.SYSDEBUG);
	
	public CascadeParentReferences(LogicRunner aLogicRunner) {
		super(aLogicRunner);
	}
	
	@Override
	public void setLogicPhase() {
		logicRunner.logicPhase = LogicRunnerPhase.CASCADE;
	}


	/**
	 * For each children role of this aParentLogicRunner:
	 * <ol>
	 * <li> cascade changes to ref'd (parent) attrs, if any
	 * <li> cascade delete (when parent is deleted)
	 * </ol>
	 * When triggering cascades, provide forwardChain info to child,
	 * used for role pruning.
	 * 
	 * @param logicRunner
	 */
	@Override
	public void execute() {

		// note: pk change is not allowed by Hibernate
		long startTime = System.nanoTime();
		if (_logSys.isDebugEnabled())  
			_logSys.debug ("#checking child cascades for", logicRunner);
		List<MetaRole>cascadeRolesDB = new ArrayList<MetaRole>();
		logicRunner.setCascadeRolesDB(cascadeRolesDB);
		
		MetaEntity parentEntity = logicRunner.getCurrentDomainObject().getMetaEntity();
		MetaModel metaModel = parentEntity.getMetaModel();
		RuleManager ruleMgr = RuleManager.getInstance(metaModel);
		Set<MetaRole> rolesToChildren = parentEntity.getRolesFromParentToChildren();
		for (MetaRole roleToChild : rolesToChildren) {
			LogicGroup childLg = ruleMgr.getLogicGroupForEntity(roleToChild.getOtherMetaEntity());
			if (childLg == null) // If child has no logic, clearly it's not interested
				continue;
			if (roleToChild.getOtherMetaRole() == null) // If child has no relationship back, it's not relevant here
				continue;
			Set<String> refdParentAttrNames = childLg.getAttributesReferencedThroughRole(roleToChild.getOtherMetaRole());
			String anyReplicatedChange = ""; 
			for (String eachAttributeName: refdParentAttrNames)  {
				if (logicRunner.getVerb() == Verb.DELETE) {
					anyReplicatedChange = "delete cascade of";
					break;
				} else if (logicRunner.getVerb() == Verb.INSERT) {
					throw new LogicException("System error - cascading for inserted parent");
				} else if (logicRunner.getVerb() == Verb.UPDATE) {
					Object oldVal = logicRunner.getPriorDomainObject().get(eachAttributeName);
					Object newVal = logicRunner.getCurrentDomainObject().get(eachAttributeName);
					if ( ! ObjectUtil.objectsAreEqual(oldVal, newVal) ) {
						anyReplicatedChange = eachAttributeName + " change (and possibly others in " + refdParentAttrNames + ") from";
						break;
					}
				} else {
					throw new LogicException("Unknown Verb: " + logicRunner.getVerb());						
				}		
			}		
			if (! "".equals(anyReplicatedChange) ) {
				cascadeToChildrenForRole(roleToChild, logicRunner, startTime, anyReplicatedChange);
			} 
		}
	}

	/**
	 * Cycle through aChildRole children for aParentLogicRunner, updating each with 
	 * LogicSource.LogicSource.CASCADED/LogicSource.CASCADE_DELETED.
	 * 
	 * @param aChildRole
	 * @param aParentLogicRunner
	 * @param aStartTime
	 * @param aReason name of attribute, is cascade delete
	 */
	private static void cascadeToChildrenForRole(MetaRole aChildRole, LogicRunner aParentLogicRunner, 
			Long aStartTime, String aReason) {
		
		String roleName = aChildRole.getRoleName();
		Collection<?> theChildren = (Collection<?>)aParentLogicRunner.getCurrentDomainObject().get(roleName);
		if (_logger.isDebugEnabled() ) 
			_logger.debug("Cascading to child " + aChildRole.getRoleName() + " since " + aReason + " parent", aParentLogicRunner);				
		aParentLogicRunner.getCascadeRolesDB().add(aChildRole);
		boolean raiseBeforeEvent = true;
		if (theChildren != null) {
			LogicTransactionContext context = aParentLogicRunner.getContext();
			PersistenceContext persistenceContext = HibernateSessionUtil.getPersistenceContextForSession(context.getSession());
			if ( ! persistenceContext.containsCollection((PersistentCollection)theChildren))
				throw new RuntimeException("Persistent collection is not in the current transaction");
			
			HibMetaEntity childMetaEntity = (HibMetaEntity)aChildRole.getOtherMetaEntity();
			HibPersistentBeanFactory beanFactory = HibPersistentBeanFactory.getInstance(
					aParentLogicRunner.getContext().getSession());
			
			for (Object eachChild: theChildren) {
				PersistentBean childBean = beanFactory.createPersistentBeanFromObject(eachChild, 
						childMetaEntity.getEntityPersister());
				cascadeToChildObject(aParentLogicRunner, childBean, aChildRole, raiseBeforeEvent);
				raiseBeforeEvent = false;
			}
		}
		if (raiseBeforeEvent) {
			aParentLogicRunner.raiseLogicRunnerEvent(LogicRunnerEventType.BEGINCASCADE, 0);
		}
		aParentLogicRunner.raiseLogicRunnerEvent(LogicRunnerEventType.ENDCASCADE, System.nanoTime() - aStartTime);
	}

	/**
	 * 	Process cascade for single child: build/run LogicRunner for logic (which may issue <code>Hibernate update</code>).
	 * <br><br>
	 * 
	 * This logic needs to consider <code>LogicTransactionContext.objectsToProcess</code>
	 * <ol>
	 * <li>Inserted rows are skipped for cascade 
	 * <blockquote>
	 * They will pick up parent values when they are processed.<br>
	 * Processing here
	 * </blockquote></li>
	 * <li>Updated rows are set for cascade: <code>objectsToProcess</code>
	 * <blockquote>
	 * If these are processed as normal updates from <code>objectsToProcess</code>, 
	 * they will <em>not</em> receive their cascade, <br>
	 * since normal updates presume that parent values are unchanged.
	 * </blockquote>
	 * </li>
	 * </ol>
	 *
	 * @param aParentLogicRunner
	 * @param aChildObjectState
	 * @param aChildRole
	 */
	private static void cascadeToChildObject(LogicRunner aParentLogicRunner, 
			PersistentBean aChildObjectState, MetaRole aChildRole, boolean aRaiseBeforeEvent) {
		
		// Check that the child is in fact pointing to the parent. This can get out of synch
		// if the FK is set, but the parent's collection is not updated, or vice-versa.
		MetaRole roleToParent = aChildRole.getOtherMetaRole();
		Object backParent = aChildObjectState.get(roleToParent.getRoleName());
		if (backParent == null)
			throw new RuntimeException("Child object " + aChildObjectState + " has a null parent object " +
					" through role " + roleToParent.getRoleName() + 
					", but it is also contained by parent " + aParentLogicRunner.getCurrentDomainObject() +
					" through role " + aChildRole.getRoleName() + 
					". When setting a relationship, it must be set at both ends, otherwise the logic " +
					"cannot execute properly.");
		HibPersistentBeanFactory beanFactory = HibPersistentBeanFactory.getInstance(
				aParentLogicRunner.getLogicContext().getSession());
		PersistentBean backParentBean = beanFactory.createPersistentBeanFromEntity(
				backParent, roleToParent.getOtherMetaEntity().getEntityName());
		if ( ! backParentBean.getPk().equals(aParentLogicRunner.getCurrentDomainObject().getPk()))
			HibernateUtil.failInconsistentRelationship(backParent, aParentLogicRunner.getCurrentDomainObject(), 
					aChildObjectState, aChildRole);

		LogicTransactionContext context = aParentLogicRunner.getContext();
		
		BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();
		
		Verb verb = aParentLogicRunner.getVerb();
		LogicSource logicSource = verb == Verb.UPDATE? LogicSource.CASCADED : LogicSource.CASCADE_DELETED; 
		
		LogicRunner childLogicRunner = context.findLogicRunner(aChildObjectState);
		if (childLogicRunner != null && childLogicRunner.getLogicProcessingState() == LogicProcessingState.QUEUED) {
			if (childLogicRunner.getVerb() == Verb.INSERT) {
				// do nothing
			} else if (childLogicRunner.getVerb() == Verb.UPDATE) {
				// make it cascade, for **DEFERRED CASCADE** via objectsToProcess TODO - make sure not already set for cascade (another parent)
				childLogicRunner.setVerb(verb);
				childLogicRunner.setCallingRoleMeta(aChildRole);
			} else
				throw new LogicException("Unexpected state (delete?) in LogicTransactionContext.objectsToProcess");
		} else {
			PersistentBean oldChild = aChildObjectState.duplicate();
			childLogicRunner = businessLogicFactory.getLogicRunner(
					context, aChildObjectState, oldChild, verb, logicSource, aParentLogicRunner, aChildRole);
			
			if (childLogicRunner != null) {
				if (aRaiseBeforeEvent) {
					childLogicRunner.raiseLogicRunnerEvent(LogicRunnerEventType.BEGINCASCADE, 0);
				}
				if (verb == Verb.UPDATE)
					childLogicRunner.update();  // note this will issue <code>Hibernate Update</code>
				else {
					childLogicRunner.delete();	
					throw new LogicException("Cascade Delete detected"); // TODO: thought we processed deletes as they came, not eagerly... cascade nullify tests?
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  CascadeParentReferences.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 