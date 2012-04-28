package com.autobizlogic.abl.engine.phase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.hibernate.metadata.ClassMetadata;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.hibernate.HibernateUtil;
import com.autobizlogic.abl.logic.BusinessLogicFactory;
import com.autobizlogic.abl.logic.BusinessLogicFactoryManager;
import com.autobizlogic.abl.logic.LogicSource;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.rule.AbstractAggregateRule;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.ObjectUtil;
import com.autobizlogic.abl.util.ProxyUtil;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * For each parent role, make aggregate (sum/count) adjustments
 * iff child data warrants (e.g., change in qualification, fk, etc).
 * <br>
 * 
 * Instances of this object iterate through the parent roles/aggregates,
 * invoking <code>SumRule/CountRule</code> (etc) to make actual adjustments.
 * <br>
 * ParentAdjustment instances maintain state for the current domain object, and
 * provide callbacks to register / set adjusted parent rows so they
 * can be saved after sum/count adjustments are made.
 */
public class AdjustAllParents extends LogicPhaseBase implements LogicPhase {
	
	private PersistentBean childDomainObject;
	private LogicRunner childLogicRunner;
	
	private PersistentBean adjustedParentDomainObject;		// adjusted current parent
	private PersistentBean adjustedOldParentDomainObject;  // old values for current parent
	
	private PersistentBean adjustedPriorParentDomainObject;			//adjusted prior parent (for reparenting)
	private PersistentBean adjustedPriorOldParentDomainObject;	// old values for prior parent

	private static final LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	private static final LogicLogger _logSys = LogicLogger.getLogger(LoggerName.SYSDEBUG);

	/**
	 * <ol>
	 * <li>
	 * For each childrenRoleToThisChild
	 * <ol>
	 * <li>
	 * For each aggregate using that role
	 * <ol>
	 * <li>
	 * invoke rule object, which
	 * <ol>
	 * <li>
	 * if changed summed/qual/fk attr, adjust parent
	 * </li>
	 * </ol>
	 * <li>if any aggregate adjustments, update parent</li>
	 * </li>
	 * </li>
	 * </li>
	 * </ol>
	 * </ol>
	 * 
	 * @return 
	 */
	public AdjustAllParents(LogicRunner aChildLogicRunner) {
		super(aChildLogicRunner);
		childLogicRunner = aChildLogicRunner;
		childDomainObject = aChildLogicRunner.getCurrentDomainObject();
	}
	
	@Override
	public void setLogicPhase() {
		logicRunner.logicPhase = LogicRunnerPhase.CASCADE;
	}
	
	/**
	 * For each parent object, see if any adjustment is required (sums, count, ...)
	 * @see  #execute(LogicRunner)
	 */
	@Override
	public void execute() {
		if (_logSys.isDebugEnabled())
			_logSys.debug ("#checking parent adjustments for", childLogicRunner);
		List<MetaRole> rtnRolesProcessed = new ArrayList<MetaRole>();
		
		BusinessLogicFactory businessLogicFactory = BusinessLogicFactoryManager.getBusinessLogicFactory();
		
		if (childDomainObject != null) {
			Set<MetaRole> childrenRoles = childDomainObject.getMetaEntity().getRolesFromChildToParents();
			for (MetaRole eachChildrenRoles: childrenRoles) {
				MetaRole roleToChild = eachChildrenRoles.getOtherMetaRole();
				if (roleToChild == null) // Is this a one-way relationship? If so, nothing to do in the parent
					continue;
				rtnRolesProcessed.add(roleToChild);
				
				MetaEntity parentEntity = roleToChild.getMetaEntity();
				
				Object parentObject = getChildLogicRunner().getCurrentDomainObject().get(eachChildrenRoles.getRoleName());
				
				// Make sure that the parent and the child point to each other
				// In the case of a delete, Hibernate will often remove the child object from
				// the parent collection by the time we get here, so we can't really check.
				if (parentObject != null && !childDomainObject.isMap() && childLogicRunner.getVerb() != Verb.DELETE) {
					Object children = ObjectUtil.getProperty(parentObject, roleToChild.getName());
					boolean badRelationship = false;
					if (children == null)
						badRelationship = true;
					else if (children instanceof Collection) {
						Collection<?> theChildren = (Collection<?>)children;
						if ( ! theChildren.contains(childDomainObject.getEntity())) {
							theChildren.contains(childDomainObject.getEntity());
							for (Object o : theChildren) {
								System.out.println("Object " + o + " == " + childDomainObject.getEntity() + 
										" : " + o.equals(childDomainObject.getEntity()));
							}
							badRelationship = true;
						}
					}
					if (badRelationship)
						HibernateUtil.failInconsistentRelationship(parentObject, 
								childDomainObject, roleToChild);
				}
				
				// In case there is some inheritance situation, get the actual parent object and
				// see what class it is.
				if (parentObject != null && !parentObject.getClass().equals(parentEntity.getEntityClass()) &&
						!parentEntity.isMap()) {
					Class<?> parentCls = ProxyUtil.getNonProxyClass(parentObject);
					ClassMetadata parentMeta = getChildLogicRunner().getContext().getSession().getSessionFactory().getClassMetadata(parentCls);
					String parentEntityName = parentMeta.getEntityName();
					parentEntity = parentEntity.getMetaModel().getMetaEntity(parentEntityName);
				}
				LogicGroup parentLg = RuleManager.getInstance(parentEntity.getMetaModel()).getLogicGroupForEntity(parentEntity);
				if (parentLg == null && parentEntity.isMap())
					continue;
				if (parentObject != null && parentLg == null) {
					SessionFactory sessFact = getChildLogicRunner().getContext().getSession().getSessionFactory();
					Class<?> parentCls = ProxyUtil.getNonProxyClass(parentObject);
					while (parentLg == null) {
						parentCls = parentCls.getSuperclass();
						if (parentCls.getName().equals("java.lang.Object"))
							break;
						ClassMetadata classMeta = sessFact.getClassMetadata(parentCls);
						if (classMeta == null)
							break;
						String parentEntityName = classMeta.getEntityName();
						parentEntity = parentEntity.getMetaModel().getMetaEntity(parentEntityName);
						parentLg = RuleManager.getInstance(parentEntity.getMetaModel()).getLogicGroupForEntity(parentEntity);
					}
				}
				if (parentLg == null)
					continue;
				Set <AbstractAggregateRule> aggregates = parentLg.findAggregatesForRole(roleToChild);
				adjustedParentDomainObject = null; // set in eachAggregate.adjustedParentDomainObject if appropriate
				adjustedOldParentDomainObject = null;
				adjustedPriorParentDomainObject = null;
				adjustedPriorOldParentDomainObject = null;
				for (AbstractAggregateRule eachAggregate: aggregates) {
					eachAggregate.adjustedParentDomainObject(this);  // do adjusts into parent (on 1st, read and return parent handle)
				}
				if (adjustedParentDomainObject != null) {		// save parent, which runs its rules (fwd chain)
					if (_logger.isDebugEnabled())  
						_logger.debug ("Adjusting parent " + adjustedParentDomainObject.toShortString() + " from", childLogicRunner);
					LogicRunner parentLogicRunner = businessLogicFactory.getLogicRunner(
								childLogicRunner.getContext(), adjustedParentDomainObject, adjustedOldParentDomainObject, 
								Verb.UPDATE, LogicSource.ADJUSTED, childLogicRunner, roleToChild);
					if (parentLogicRunner != null)
						parentLogicRunner.update();
				}
				
				if (adjustedPriorParentDomainObject != null) {		// save PRIOR parent, which runs its rules (fwd chain) FIXME REPARENT
					if (_logger.isDebugEnabled())  
						_logger.debug ("Adjusting PRIOR Parent " + adjustedPriorParentDomainObject.toShortString() + " from", childLogicRunner);
					LogicRunner oldParentLogicRunner = businessLogicFactory.getLogicRunner( 
							childLogicRunner.getContext(), adjustedPriorParentDomainObject, adjustedPriorOldParentDomainObject, 
							Verb.UPDATE, LogicSource.ADJUSTED, childLogicRunner, roleToChild);
					if (oldParentLogicRunner != null)
							oldParentLogicRunner.update();  // See above
				}  // adjustedOldParentDomeainObject != null
			}  // eachChildrenRoles
		} // childDomainObject != null

		childLogicRunner.setAdjustedRolesDB(rtnRolesProcessed);
	}

	/**
	 * This is called by sum/countRule when they determine that a parent needs an update for adjustment.
	 * <br>
	 * Setting this causes the eventual update of the parent when all the sums/counts have been analyzed
	 * for a given relationship.
	 * 
	 * <blockquote>
	 * See <code>adjustAllParents</code> above: <code>eachAggregate.adjustedParentDomainObject(this)</code>
	 * </blockquote>
	 * 
	 * It may be called several times for each parent, 
	 * since a given relationship may have multiple sums/counts.
	 * <blockquote>
	 * Observe subsequent calls for same parent may be a different instance,<br>
	 * since instance reflects a state change.
	 * </blockquote>
	 * 
	 * @param anAdjustedParentDomainObject
	 * @return aDomainObject - callback for Sum/CountRule to save retrieved/altered parent
	 */
	public Object setAdjustedParentDomainObject (PersistentBean anAdjustedParentDomainObject) {
		if (anAdjustedParentDomainObject == null) {
			// 1st sum might change, 2nd might not - still want to update!
		} else {
			if (adjustedParentDomainObject != null && adjustedParentDomainObject != anAdjustedParentDomainObject) {
				// domainObject has new identity for each adjustment, since it reflects each incremental state
				// so, even though identity changes, it's referring to the same parent - so, *don't*....
				// throw new LogicException("Business Logic System Error - unexpected parent identity change.");
			}
			adjustedParentDomainObject = anAdjustedParentDomainObject;
		}
		return anAdjustedParentDomainObject;
	}


	/**
	 *
	 * @param anOldParentDomainObject
	 * @return aDomainObject - callback for Sum/Count to set old parent - makes copy of old parent
	 */
	public Object setOldAdjustedParentDomainObject (PersistentBean anOldParentDomainObject) {
		if (adjustedOldParentDomainObject == null) {
			if (anOldParentDomainObject != null)
				adjustedOldParentDomainObject = anOldParentDomainObject.duplicate();
			else
				adjustedOldParentDomainObject = null;
		}
		return anOldParentDomainObject;
	}


	/**
	 * Same as see also below, except for *prior* parent (case: reparenting)
	 * 
	 * @see #setAdjustedParentDomainObject(ObjectState)
	 * 
	 * @param anAdjustedParentDomainObject
	 * @return aDomainObject - callback for Sum/CountRule to save retrieved/altered parent
	 */
	public Object setPriorAdjustedParentDomainObject (PersistentBean anAdjustedParentDomainObject) {
		if (anAdjustedParentDomainObject == null) {
			// 1st sum might change, 2nd might not - still want to update!
		} else {
			if (adjustedPriorParentDomainObject != null && adjustedPriorParentDomainObject != anAdjustedParentDomainObject) {
				// domainObject has new identity for each adjustment, since it reflects each incremental state
				// so, even though identity changes, it's referring to the same parent - so, *don't*....
				// throw new LogicException("Business Logic System Error - unexpected parent identity change.");
			}
			adjustedPriorParentDomainObject = anAdjustedParentDomainObject;
		}
		return anAdjustedParentDomainObject;
	}


	/**
	 *
	 * @param anOldParentDomainObject
	 * @return aDomainObject - callback for Sum/Count to set old parent - makes copy of old parent
	 */
	public Object setPriorOldAdjustedParentDomainObject (PersistentBean anOldParentDomainObject) {
		if (adjustedPriorOldParentDomainObject == null) {
			if (anOldParentDomainObject != null)
				adjustedPriorOldParentDomainObject = anOldParentDomainObject.duplicate();
			else
				adjustedPriorOldParentDomainObject = null;
		}
		return anOldParentDomainObject;
	}
	
	public LogicRunner getChildLogicRunner() {
		return childLogicRunner;
	}


	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  AdjustAllParents.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 