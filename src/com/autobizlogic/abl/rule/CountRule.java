package com.autobizlogic.abl.rule;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.engine.PersistenceContext;
import org.hibernate.impl.SessionImpl;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.metadata.hibernate.HibMetaEntity;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.hibernate.HibernateSessionUtil;
import com.autobizlogic.abl.engine.phase.AdjustAllParents;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.engine.LogicRunner.LogicProcessingState;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.session.LogicTransactionManager;
import com.autobizlogic.abl.util.BeanMap;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.ProxyUtil;

/**
 * Define and execute a Count business rule.
 */

public class CountRule extends AbstractAggregateRule {
	
	protected CountRule(LogicGroup logicGroup, String logicMethodName, String roleName, String clause, String beanAttributeName) {
		super(logicGroup, logicMethodName, roleName, clause, beanAttributeName);
	}
	
	@Override
	public void adjustedParentDomainObject(AdjustAllParents aParentAdjustments) {
		
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		if (childLogicRunner.getVerb() == Verb.UPDATE)
			adjustFromUpdatedChild(aParentAdjustments);
		else if (childLogicRunner.getVerb() == Verb.INSERT)
			adjustFromInsertedChild(aParentAdjustments);
		else if (childLogicRunner.getVerb() == Verb.DELETE)
			adjustFromDeletedChild(aParentAdjustments);
		else
			throw new LogicException("Unexpected Verb: " + childLogicRunner.getVerb());
	}
	
	public void adjustFromUpdatedChild(AdjustAllParents aParentAdjustments) {
		
		long startTime = System.nanoTime();
		MetaAttribute metaAttribute = getLogicGroup().getMetaEntity().getMetaAttribute(getBeanAttributeName());
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();
		
		PersistentBean priorChild = childLogicRunner.getPriorDomainObject();
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		
		String otherRoleName = role.getOtherMetaRole().getRoleName();
		Object priorParent = priorChild.get(otherRoleName);
		Object currentParent = currentChild.get(otherRoleName);
		
		// If there was no parent, and there still isn't, we have nothing to adjust
		if (priorParent == null && currentParent == null)
			return;
		
		// I hate this because it's Hibernate-dependent code and I'd like to keep it at a higher
		// level, but I can't think of an elegant way of doing that right now.
		HibMetaEntity parentMetaEntity = (HibMetaEntity)role.getMetaEntity();
		PersistentBean priorParentBean;
		if (priorParent instanceof PersistentBean)
			throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
			
		SessionImpl sessionImpl = (SessionImpl)context.getSession();
		if ( ! (currentParent instanceof Map))
			currentParent = ProxyUtil.getNonProxyObject(currentParent);
		
		EntityPersister ep;
		if (currentChild.isMap()) {
			ep = parentMetaEntity.getEntityPersister();
		}
		else {
			String entityName = sessionImpl.getEntityName(currentParent);
			if ( ! (currentParent instanceof Map))
				currentParent = ProxyUtil.getNonProxyObject(currentParent);
			ep = sessionImpl.getEntityPersister(entityName, currentParent);
		}
		priorParentBean = HibPersistentBeanFactory.getInstance(context.getSession()).
			createPersistentBeanFromObject(priorParent, ep);
		
		PersistentBean currentParentBean;
		if (priorParent instanceof PersistentBean)
			throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
		
		currentParentBean = HibPersistentBeanFactory.getInstance(context.getSession()).
			createPersistentBeanFromObject(currentParent, parentMetaEntity.getEntityPersister());

		// Look ahead in the LogicRunner queue for a LogicRunner for the
		// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
		boolean priorCascadeDeferred = false;
		boolean currentCascadeDeferred = false;
		LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(priorParentBean);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				priorParentBean = aheadLogicRunner.getCurrentDomainObject();
				priorCascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade for prior parent update (from count) will be deferred since the prior parent object is already scheduled for processing");
			}
		}
		aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(currentParentBean);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				currentParentBean = aheadLogicRunner.getCurrentDomainObject();
				currentCascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade for current parent update (from count) will be deferred since the current parent object is already scheduled for processing");
			}
		}

		// Adjust new by delta (if any) - TODO - other algorithms
		boolean priorQual = runQualificationForBean(priorChild);
		boolean currQual = runQualificationForBean(currentChild);  	//todo - can possibly prune per dependsOn roles (needs thought)
		
		// If the child was not part of the equation in the old state, and it still isn't, we're done
		if (!priorQual && !currQual)
			return;
		
		boolean parentsAreEqual = BeanUtil.beansAreEqual(role.getMetaEntity(), priorParent, currentParent);
		
		// Start with adjusting the old parent, if any
		// This is done only if:
		// 1 - There was an old parent
		// 2 - The old parent is not the same as the new parent
		// 3 - The child qualified in the old state
		if (priorParent != null && !parentsAreEqual && priorQual) {
			// we are definitely adjusting a prior parent - need its old values
			//ObjectState priorParentOldValues = priorParent.copy();
			PersistentBean priorParentOldValues = priorParentBean.duplicate();
			
			// For transient attribute, we just reset them to null, and they'll get 
			// recomputed when needed.
			if (metaAttribute.isTransient()) {
				priorParentBean.put(getBeanAttributeName(), null);
			}
			else {
			
				Number theCount = (Number)priorParentBean.get(getBeanAttributeName());
				if (theCount == null) theCount = 0;
				Number theNewCount = theCount.longValue()  - 1;
				if (theNewCount.intValue() < 0)
					throw new RuntimeException("Impossible situation : count cannot be less than zero - " + priorParent);
				priorParentBean.put(getBeanAttributeName(), theNewCount.intValue());
				firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
						aParentAdjustments.getChildLogicRunner(), priorParentBean, theCount, 
						System.nanoTime() - startTime);
				startTime = System.nanoTime(); // Restart the clock
			}
			if (log.isInfoEnabled())  {
				String parentClassNameDB = NodalPathUtil.getNodalPathLastName(priorParentBean.getEntityName());
				log.info ("Adjusting count " + parentClassNameDB + '.' + getBeanAttributeName() + "-= 1" +
						" in PRIOR parent from updated/reparented child", childLogicRunner );
			}
			
			if ( ! noCode)  // added by Val - code review please  FIXME REPARENT
				invokeLogicMethod(priorParentBean, null, childLogicRunner);
			
			if ( ! priorCascadeDeferred) {
				aParentAdjustments.setPriorAdjustedParentDomainObject(priorParentBean);  // cause it to be saved
				aParentAdjustments.setPriorOldAdjustedParentDomainObject(priorParentOldValues);
			}
			
		}  	// end reparenting - adjust prior parent
		
		// Obviously if there is no current parent, there is nothing more to adjust
		if (currentParent == null)
			return;
		
		Integer currentAdjustBy = 0;
		
		// If this is a change of parent, does the child qualify?
		if ( ! parentsAreEqual) {
			if (currQual)
				currentAdjustBy = 1;
		}
		else { // The parent is the same -- did the qualification change?
			if (priorQual && !currQual)
				currentAdjustBy = -1;
			else if (!priorQual && currQual)
				currentAdjustBy = 1;
		}
		
		if (currentAdjustBy != 0) {
			if ( ! currentCascadeDeferred)
				aParentAdjustments.setOldAdjustedParentDomainObject(currentParentBean);
			Number oldCount = (Number)currentParentBean.get(getBeanAttributeName());
			
			// For transient attribute, we just reset them to null, and they'll get 
			// recomputed when needed.
			if (metaAttribute.isTransient()) {
				currentParentBean.put(getBeanAttributeName(), null);
			}
			else {

				if (oldCount == null) oldCount = 0;  // no need to initialize aggregates
				Number theNewCount = oldCount.longValue() + currentAdjustBy;
				if (theNewCount.intValue() < 0)
					throw new RuntimeException("Impossible situation : count cannot be less than zero - " + priorParent);
				currentParentBean.put(getBeanAttributeName(), theNewCount.intValue());
				if ( ! currentCascadeDeferred)
					aParentAdjustments.setAdjustedParentDomainObject (currentParentBean);  // cause it to be saved
			}
			if (log.isInfoEnabled())  {
				String parentClassNameDB = NodalPathUtil.getNodalPathLastName(currentParentBean.getEntityName());
				log.info ("Adjusting count " + parentClassNameDB  + '.' + getBeanAttributeName() + "+=" + 
						currentAdjustBy + " from updated child", childLogicRunner );
				}
			
			if ( ! noCode)
				invokeLogicMethod(currentParentBean, priorParentBean, childLogicRunner);
			
			firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
					aParentAdjustments.getChildLogicRunner(), currentParentBean, oldCount, 
					System.nanoTime() - startTime);
		}
	}
	
	public void adjustFromInsertedChild(AdjustAllParents aParentAdjustments) {

		long startTime = System.nanoTime();
		MetaAttribute metaAttribute = getLogicGroup().getMetaEntity().getMetaAttribute(getBeanAttributeName());
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();
		MetaRole theRole = getRole().getOtherMetaRole();
		if (theRole == null)
			throw new RuntimeException("No such role: " + roleName + " defined for " + 
					getLogicGroup().getMetaEntity().getEntityName() + " for a count");
		MetaRole roleFromParent = getRole();
		if (roleFromParent == null)
			return;

		// adjust new by delta (if any) - TODO - other algorithms
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		boolean currQual = runQualificationForBean(currentChild);  	//todo - can possibly prune per dependsOn roles (needs thought)
		if (currQual ==  false)
			return; // not qualified - no adjustment

		boolean cascadeDeferred = false;
		String parentRoleName = theRole.getRoleName();
		Object currentParent = currentChild.get(parentRoleName);
		
		if (currentParent != null) {
			// I hate this because it's Hibernate-dependent code and I'd like to keep it at a higher
			// level, but I can't think of an elegant way of doing that right now.
			PersistentBean theParent;
			if (currentParent instanceof PersistentBean)
				throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
//			HibMetaEntity otherMetaEntity = (HibMetaEntity)theRole.getOtherMetaEntity();
			
			SessionImpl sessionImpl = (SessionImpl)context.getSession();
			String entityName = sessionImpl.getEntityName(currentParent);
			EntityPersister ep = sessionImpl.getEntityPersister(entityName, currentParent);
			theParent = HibPersistentBeanFactory.getInstance(context.getSession()).
				createPersistentBeanFromObject(currentParent, ep);

			// Look ahead in the LogicRunner queue for a LogicRunner for the
			// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
			LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(theParent);
			if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
				if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
					theParent = aheadLogicRunner.getCurrentDomainObject();
					cascadeDeferred = true;
					if (log.isInfoEnabled())
						log.info("Cascade of parent update (from count) will be deferred since the parent object is already scheduled for processing");
				}
			}
	
			if ( ! cascadeDeferred)
				aParentAdjustments.setOldAdjustedParentDomainObject(theParent);
			
			Number oldCount = (Number)theParent.get(getBeanAttributeName());

			// For transient attributes, we just set them to null so that they are recomputed when needed
			if (metaAttribute.isTransient()) {
				theParent.put(getBeanAttributeName(), null);
			}
			else {
				if (oldCount == null) oldCount = 0;  // no need to initialize aggregates
				int theNewCount = oldCount.intValue() + 1;
				theParent.put(getBeanAttributeName(), theNewCount);
				if ( ! cascadeDeferred)
					aParentAdjustments.setAdjustedParentDomainObject (theParent);  // cause it to be saved
				if (log.isInfoEnabled()) {
					String parentClassNameDB = NodalPathUtil.getNodalPathLastName(theParent.getEntityName());
					log.info ("Adjusting count " +  parentClassNameDB + '.' + getBeanAttributeName() + 
							"+=1 from inserted child", childLogicRunner );
				}
			}

			if ( ! noCode)
				invokeLogicMethod(theParent, null, childLogicRunner);

			firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
					aParentAdjustments.getChildLogicRunner(), theParent, oldCount, System.nanoTime() - startTime);
		}
	}
	
	public void adjustFromDeletedChild(AdjustAllParents aParentAdjustments) {
		
		long startTime = System.nanoTime();
		MetaAttribute metaAttribute = getLogicGroup().getMetaEntity().getMetaAttribute(getBeanAttributeName());
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();

		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		//PersistentBean originalChild = context.getDeletedObjectState(currentChild);
		
		boolean currQual = runQualificationForBean(currentChild);  //TODO - can possibly prune per dependsOn roles (needs thought)
		if (currQual ==  false)
			return; // not qualified - no adjustment

		MetaRole theRole = getRole().getOtherMetaRole();
		if (theRole == null)
			throw new RuntimeException("Unable to find role for count rule " + this);

		// adjust new by delta (if any) - TODO - other algorithms
		Object parent = currentChild.get(theRole.getRoleName());
		boolean cascadeDeferred = false;

		// I hate this because it's Hibernate-dependent code and I'd like to keep it at a higher
		// level, but I can't think of an elegant way of doing that right now.
		EntityPersister ep;
		SessionImpl sessionImpl = (SessionImpl)context.getSession();
		if (sessionImpl.contains(parent)) {
			String entityName = sessionImpl.getEntityName(parent);
			ep = sessionImpl.getEntityPersister(entityName, parent);
		}
		else {
			HibMetaEntity parentMetaEntity = (HibMetaEntity)role.getMetaEntity();
			ep = parentMetaEntity.getEntityPersister();
		}
		PersistentBean theParent = HibPersistentBeanFactory.getInstance(context.getSession()).
				createPersistentBeanFromObject(parent, ep);

		if (theParent == null || context.objectIsDeleted(theParent))
			return;
		
		// Look ahead in the LogicRunner queue for a LogicRunner for the
		// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
		LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(theParent);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				theParent = aheadLogicRunner.getCurrentDomainObject();
				cascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade of parent update (from count delete) will be deferred since " + 
							"the parent object is already scheduled for processing");
			}
		}

		if ( ! cascadeDeferred)
			aParentAdjustments.setOldAdjustedParentDomainObject(theParent);
		Number oldCount = (Number)theParent.get(getBeanAttributeName());

		// For transient attribute, we just reset them to null, and they'll get 
		// recomputed when needed.
		if (metaAttribute.isTransient()) {
			theParent.put(getBeanAttributeName(), null);
		}
		else {
			if (oldCount == null) oldCount = 0;  // no need to initialize aggregates
			int theNewCount = oldCount.intValue() - 1;
			if (theNewCount < 0)
				throw new RuntimeException("Count attribute " + getBeanAttributeName() + " in object " + 
						theParent + " cannot have a value less than 0");
			theParent.put(getBeanAttributeName(), theNewCount);
		}
		if (log.isInfoEnabled()) {
			String parentClassNameDB = NodalPathUtil.getNodalPathLastName(theParent.getEntityName());
			log.info ("Adjusting count " + parentClassNameDB + '.'  +getBeanAttributeName() +
					"-=1 from deleted child", childLogicRunner );
		}

		if ( ! noCode)
			invokeLogicMethod(theParent, null, childLogicRunner);

		firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
				aParentAdjustments.getChildLogicRunner(), theParent, oldCount, System.nanoTime() - startTime);
		
		if ( ! cascadeDeferred)
			aParentAdjustments.setAdjustedParentDomainObject (theParent);  // cause it to be saved
	}
	
	/* COMMENTED OUT until we have a better idea of how this is supposed to work.
	public void computeAggregateFromDatabase(Object bean, LogicTransactionContext context) {
		//BeanMap beanMap = new BeanMap(bean);
		String beanClassName = HibernateMetaUtil.getEntityNameForObject(bean); // objectState.getObjectClassName();
		//String attName = this.getBeanAttributeName();
		String roleName = this.getRoleName();
		String[] inverse = context.getMetaUtil().getInverseForRole(beanClassName, roleName);
		ClassMetadata childClassMeta = context.getSessionFactory().getClassMetadata(inverse[0]);
		String childEntityName = childClassMeta.getEntityName();
		String childRoleName = inverse[1];
		String whereClause = this.getQualificationSQL();
		if (whereClause == null)
			whereClause = "";
		else
			whereClause = " and (" + whereClause + ")";
		String sql = "select count(*) from " + childEntityName + " where " + childRoleName + " = :parent" + whereClause;
		
		FlushMode oldFlushMode = context.getSession().getFlushMode();
		context.getSession().setFlushMode(FlushMode.MANUAL);
		
		Query query = context.getSession().createQuery(sql);
		query.setEntity("parent", bean);
		Long count = (Long)query.list().get(0);
		//beanMap.put(attName, count);
		
		context.getSession().setFlushMode(oldFlushMode);
		
		// return count;
	}
	
	public void computeAggregateFromDatabase2(Object bean, LogicTransactionContext context) {
		BeanMap beanMap = new BeanMap(bean);
		@SuppressWarnings("unchecked")
		Collection<Object> value = (Collection<Object>)beanMap.get(getRoleName());
		int size = value.size();
		//if (value instanceof List<?>) { // Lists can have null elements, so we have to count the non-null elements
		//	for (Object obj : value) {
		//		if (obj != null)
		//			size++;
		//	}
		//}
		//if (value instanceof PersistentList) {
		//	((PersistentList)value).clearDirty();
		//}
		//else
		//	throw new RuntimeException("Collection is not a persistent list");
		beanMap.put(getBeanAttributeName(), size);
	}
	*/
	
	/**
	 * Mechanism to initialize count attribute, e.g., from Domain Object getter.
	 * 
	 * @param aParentDomainObject Domain Object containing the sum
	 * @param anAttributeName name of count attribute
	 * @param aSessionFactory non EE may require global static, EE can use 
	 * sf = (SessionFactory)new InitialContext().lookup("MySessionFactory")
	 * @return
	 */
	public Integer getCountValue(PersistentBean aParentDomainObject, String anAttributeName, 
			SessionFactory aSessionFactory) {
		
		CountRule countRule = null;
		Session session = aSessionFactory.getCurrentSession(); //   aContext.getSession();
		Transaction transaction = session.getTransaction();
		LogicTransactionContext context = LogicTransactionManager.
				getCurrentLogicTransactionContextForTransaction(transaction, session);
		Set<MetaRole> childRoles = aParentDomainObject.getMetaEntity().getRolesFromParentToChildren();
		for (MetaRole eachChildRole: childRoles) {
			Set <AbstractAggregateRule> aggregates = logicGroup.findAggregatesForRole (eachChildRole);
			for (AbstractAggregateRule eachAggregate: aggregates) {
				if (eachAggregate.getBeanAttributeName().equalsIgnoreCase(anAttributeName)) {
					countRule = (CountRule) eachAggregate;
					break;
				}
			}
			if (countRule != null)
				break;
		}
		if (countRule == null)
			throw new LogicException("No Sum definition found for " + anAttributeName);

		return countRule.computeCountInMemory(aParentDomainObject, context); 
	}

	
	/**
	 * Compute the count in-memory, i.e. without directly accessing the database. This is used for
	 * transient attributes.
	 * @param bean The parent object
	 * @param aContext The current context
	 * @return The count value
	 */

	public Integer computeCountInMemory(Object bean, LogicTransactionContext aContext) {
		BeanMap beanMap = new BeanMap(bean);
		int resultNumber = 0;
		Collection<?> theChildren = (Collection<?>)beanMap.get(roleName);
		
		if (theChildren == null || theChildren.isEmpty())
			return 0;
		
		PersistenceContext persistenceContext = HibernateSessionUtil.getPersistenceContextForSession(aContext.getSession());
		if ( ! persistenceContext.containsCollection((PersistentCollection)theChildren)) {
			String errMsg = "Persistent collection " + roleName + " of object " + bean + " is not in the current transaction. This usually means " +
			"that you are trying to use a transient sum or count in a commit action or commit constraint, and the object in question is being deleted. " +
			"You should change your constraint or action (in this case, " + getLogicGroup().getLogicClassName() + "." + getLogicMethodName() +
			") so that it does not fire when the object is being deleted (e.g. logicContext.verb != Verb.DELETE).";
			throw new RuntimeException(errMsg);
		}
		
/* Note : I am commenting this out until we have a better idea of how PersistentBeans are handled
		for (Object eachChild: theChildren) {
			ObjectState childObjectState = ObjectStateFactory.createObjectStateForObject(eachChild, aContext.getSession());
			if (childObjectState == null) // This usually means the child has not yet been saved, and therefore has no pk yet. We'll get to it later.
				return null;
			
			if (runQualificationForBean(childObjectState))
				resultNumber++;
				
		} // for eachChild
*/		
		if (log.isInfoEnabled())
			log.info ("InMem[" + getBeanAttributeName() + "]==" + resultNumber + 
					" in parent " + beanMap.toString());

		return resultNumber;		
	}
	
	/**
	 * This gets called by the pre delete listener for non-persistent counts.
	 * The problem is that, for non-persistent attributes, by the time the normal business logic gets invoked,
	 * deleted objects are gone from memory and from the database, and therefore there is no way
	 */
/*	public void adjustForDeletedChild(Object child, LogicTransactionContext context) {
		boolean currQual = runQualificationForBean(child);
		if (currQual ==  false)
			return; // not qualified - no adjustment

		String fullyQualifiedRoleName = logicGroup.beanClassName + "." + roleName;
		RoleMeta roleMeta = new RoleMeta( fullyQualifiedRoleName, context);

		Object theParent = currentChild.get(roleMeta.getChildRoleName());
		
		if (theParent != null) {
			Number theCount = (Number)theParent.get(getBeanAttributeName());
			if (theCount == null) theCount = 0;  // no need to initialize aggregates
			int theNewCount = theCount.intValue() - 1;
			if (theNewCount < 0)
				throw new RuntimeException("Count attribute " + getBeanAttributeName() + " in object " + theParent + " cannot have a value less than 0");
			theParent.put(getBeanAttributeName(), theNewCount);
			if (log.isInfoEnabled())
					log.info ("Adjusting non-persistent count attribute: " + getBeanAttributeName() + "-=1 in parent proxy: " +  theParent.getObjectClass().getSimpleName() + " from deleted child");
		}
} */

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Count " + getLogicGroup().getMetaEntity().getEntityName() + "#" + 
				this.getLogicMethodName()+ ", counting over role " + this.getRoleName());
		if (getQualification() != null && getQualification().trim().length() > 0)
			sb.append(" where " + this.getQualification());
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  CountRule.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 