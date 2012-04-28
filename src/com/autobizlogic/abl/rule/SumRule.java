package com.autobizlogic.abl.rule;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.metadata.MetaAttribute;
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
import com.autobizlogic.abl.util.BeanMap;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.NumberUtil;
import com.autobizlogic.abl.util.ProxyUtil;

import org.hibernate.collection.PersistentCollection;
import org.hibernate.engine.PersistenceContext;
import org.hibernate.impl.SessionImpl;
import org.hibernate.persister.entity.EntityPersister;

/**
 * Each instance represents the definition of a specific sum rule,<br>
 * with behavior <code>adjusttedParentDomainObject</code>, to run the rule.
 * <p>
 * 
 * Sum Rules are called by child objects via LogicRunner/AdjustAllParents,<br>
 *  to execute the adjustments to a parent.  They do *not* update the parent,
 *  that is done by the caller after *all* sum/counts are processed for
 *  a particular relationship (i.e., we "batch" the adjustments).
 * 
 */
public class SumRule extends AbstractAggregateRule {

	private String summedField;

	protected SumRule(LogicGroup logicGroup, String logicMethodName, String roleName, String clause, String summedField, String beanAttributeName) {
		super(logicGroup, logicMethodName, roleName, clause, beanAttributeName);
		this.summedField = summedField;
	}

	/**
	 * Get the name of the child attribute that is summed up by this rule.
	 */
	public String getSummedFieldName() {
		return summedField;
	}

	/**
	 * Executes sum rule
	 * <ol>
	 * <li>Check child's summed, qual, pk</li>
	 * <li>If altered
	 * <ol>
	 * <li>Read Parent (likely cached)</li>
	 * <li>Adjust old/new sum</li>
	 * <li>aParentAdjustments.setAdjustedParentDomainObject - causes parent update after all aggregates 
	 * along this role</li>
	 * </li>
	 * </ol>
	 * </ol>
	 * @param aParentAdjustments
	 */
	@Override
	public void adjustedParentDomainObject(AdjustAllParents aParentAdjustments) {
		// return
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


	private void adjustFromInsertedChild(AdjustAllParents aParentAdjustments) {

		long startTime = System.nanoTime();
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();
		
		BigDecimal currSummedD;
		// adjust new by delta (if any) - TODO - other algorithms
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		boolean currQual = runQualificationForBean(currentChild);  	//todo - can possibly prune per dependsOn roles (needs thought)
		if (currQual ==  false)
			return;  // not qualified: no adjustment
		currSummedD = getObjectPropertyAsBigDecimal(currentChild, summedField);
		BigDecimal adjustment = BigDecimal.ZERO;
		if (currSummedD.equals(adjustment))
			return; // summed value is 0: no adjustment
		adjustment = currSummedD;

		if ( ! getRole().isCollection())
			throw new RuntimeException("Cannot sum a non-collection relationship: " + getRole());
		String parentRoleName = getRole().getOtherMetaRole().getRoleName();
		Object theParent = currentChild.get(parentRoleName);
		PersistentBean parentBean = null;
		boolean cascadeDeferred = false;
		
		if (theParent != null) {
			if (theParent instanceof PersistentBean)
				parentBean = (PersistentBean)theParent;
			else {
				HibMetaEntity otherMetaEntity = (HibMetaEntity)getRole().getMetaEntity();
//				SessionImpl sessionImpl = (SessionImpl)context.getSession();
//				String entityName = sessionImpl.getEntityName(theParent);
//				EntityPersister ep = sessionImpl.getEntityPersister(entityName, theParent);
				parentBean = HibPersistentBeanFactory.getInstance(context.getSession()).
					createPersistentBeanFromObject(theParent, otherMetaEntity.getEntityPersister());
			}

			// Look ahead in the LogicRunner queue for a LogicRunner for the
			// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
			LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(parentBean);
			if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
				if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
					parentBean = aheadLogicRunner.getCurrentDomainObject();
					cascadeDeferred = true;
					if (log.isDebugEnabled())
						log.debug("Parent logic chaining deferred, since is already scheduled for processing", childLogicRunner);
				}
			}
			
			if ( ! cascadeDeferred)
				aParentAdjustments.setOldAdjustedParentDomainObject(parentBean);
			
			BigDecimal theSum = BigDecimal.ZERO;

			// For transient attributes, we just set them to null so that they are recomputed when needed
			MetaAttribute metaAttribute = getLogicGroup().getMetaEntity().getMetaAttribute(getBeanAttributeName());
			if (metaAttribute.isTransient()) {
				parentBean.put(getBeanAttributeName(), null);
			}
			else {
				theSum = getObjectPropertyAsBigDecimal(parentBean, getBeanAttributeName());
				if (theSum == null) 
					theSum = BigDecimal.ZERO;  // no need to initialize aggregates
				BigDecimal theNewSumD = theSum.add(adjustment);
				Class<?> attType = parentBean.getMetaEntity().getMetaAttribute(getBeanAttributeName()).getType();
				Number theNewNewSum = NumberUtil.convertNumberToType(theNewSumD, attType);
				parentBean.put(getBeanAttributeName(), theNewNewSum);
			}
			if (log.isDebugEnabled())
				log.debug ("Adjusting sum " +  theParent.getClass().getSimpleName() +
						"." + getBeanAttributeName() + "+=" + adjustment + " from updated child", childLogicRunner );

			if ( ! noCode)
				invokeLogicMethod(parentBean, null, childLogicRunner);

			firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
					aParentAdjustments.getChildLogicRunner(), parentBean, theSum, System.nanoTime() - startTime);
		}
		
		if ( ! cascadeDeferred)
			aParentAdjustments.setAdjustedParentDomainObject(parentBean);  // cause it to be saved
	}


	private void adjustFromUpdatedChild(AdjustAllParents aParentAdjustments) {

		long startTime = System.nanoTime();
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();

		// adjust new by delta (if any) - TODO - other algorithms (SQL, InMemory)
		PersistentBean priorChild = childLogicRunner.getPriorDomainObject();
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();

		// Determine whether the child used to be our child  (this does not issue SQLs)
		String roleToParentName = getRole().getOtherMetaRole().getRoleName();
		Object priorParent = priorChild.get(roleToParentName);
		Object currentParent = currentChild.get(roleToParentName);

		// If there was no parent, and there still isn't, we have nothing to adjust
		if (priorParent == null && currentParent == null)
			return;

		boolean priorCascadeDeferred = false;
		boolean currentCascadeDeferred = false;
		HibMetaEntity parentMetaEntity = (HibMetaEntity)role.getMetaEntity();
		
		PersistentBean priorParentPersBean;
		if (priorParent instanceof PersistentBean)
			throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
		
		SessionImpl sessionImpl = (SessionImpl)context.getSession();
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
		priorParentPersBean = HibPersistentBeanFactory.getInstance(context.getSession()).
			createPersistentBeanFromObject(priorParent, ep);
		
		PersistentBean currentParentPersBean;
		if (priorParent instanceof PersistentBean)
			throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
		
		currentParentPersBean = HibPersistentBeanFactory.getInstance(context.getSession()).
			createPersistentBeanFromObject(currentParent, parentMetaEntity.getEntityPersister());
		
		// Look ahead in the LogicRunner queue for a LogicRunner for the
		// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
		LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(priorParentPersBean);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				priorParentPersBean = aheadLogicRunner.getCurrentDomainObject();
				priorCascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade of old parent update (from sum update) will be deferred since the old parent object is already scheduled for processing");
			}
		}
		aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(currentParentPersBean);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				currentParentPersBean = aheadLogicRunner.getCurrentDomainObject();
				currentCascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade of current parent update (from sum update) will be deferred since the current parent object is already scheduled for processing");
			}
		}

		// There are three dimensions to keep in mind here:
		// - the child may have changed parent (none -> one, one -> none, one -> different one)
		// - the summed attribute may have changed value
		// - the qualification condition may have changed

		// Start by determining whether the old and new child objects satisfy the condition
		boolean priorQual = runQualificationForBean(priorChild);
		boolean currQual = runQualificationForBean(currentChild);  	//todo - can possibly prune per dependsOn roles (needs thought)

		// If the child was not part of the equation in the old state, and it still isn't, we're done
		if (!priorQual && !currQual)
			return;

		// Determine the type of the sum attribute
		MetaAttribute metaAttribute = logicGroup.getMetaEntity().getMetaAttribute(getBeanAttributeName());
		Class<?> attType = metaAttribute.getType();
		
		boolean oldAndNewParentsAreEqual = BeanUtil.beansAreEqual(parentMetaEntity, priorParent, currentParent);

		// Start with adjusting the old parent, if any
		// This is done only if:
		// 1 - There was an old parent
		// 2 - The old parent is not the same as the new parent
		// 3 - The child qualified in the old state

		if (priorParent != null && !oldAndNewParentsAreEqual && priorQual) { // reparenting - adjust prior parent
			// we are definitely adjusting a prior parent - need its old values
			// the above should not require a getParent sql, but we need all the values now (fetched below)
			PersistentBean priorParentOldValues = priorParentPersBean.duplicate();

			BigDecimal oldChildValue = getObjectPropertyAsBigDecimal(priorChild, summedField);
			if (oldChildValue == null)
				oldChildValue = BigDecimal.ZERO;
			BigDecimal oldParentValue = getObjectPropertyAsBigDecimal(priorParentPersBean, getBeanAttributeName());
			if (oldParentValue == null)
				oldParentValue = BigDecimal.ZERO;
			BigDecimal oldParentNewValue = oldParentValue.subtract(oldChildValue);
			if ( ! oldParentNewValue.equals(oldParentValue)) {

				// For transient attribute, we just reset them to null, and they'll get 
				// recomputed when needed.
				if (metaAttribute.isTransient()) {
					priorParentPersBean.put(getBeanAttributeName(), null);
				}
				else {
					Number theNewNewSum = NumberUtil.convertNumberToType(oldParentNewValue, attType);
					priorParentPersBean.put(getBeanAttributeName(), theNewNewSum);
				}
				firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
						aParentAdjustments.getChildLogicRunner(), priorParentPersBean, 
						oldParentValue, System.nanoTime() - startTime);
				startTime = System.nanoTime(); // Restart the clock
			}
			if (log.isDebugEnabled())  {
				String parentClassNameDB = NodalPathUtil.getNodalPathLastName(parentMetaEntity.getEntityName());
				log.debug ("Adjusting sum " +  parentClassNameDB +
						"." + getBeanAttributeName() + "-=" + oldChildValue + " from updated/reparented child", childLogicRunner );
			}

			if ( ! noCode)
				invokeLogicMethod(priorParentPersBean, null, childLogicRunner);

			if ( ! priorCascadeDeferred) {
				aParentAdjustments.setPriorAdjustedParentDomainObject (priorParentPersBean);  // cause it to be saved
				aParentAdjustments.setPriorOldAdjustedParentDomainObject(priorParentOldValues);
			}

		}  	// end reparenting - adjust prior parent

		// Obviously if there is no current parent, there is nothing more to adjust
		// Open question: what happens if the parent is deleted?
		if (currentParent == null)
			return;

		BigDecimal currentAdjustBy = BigDecimal.ZERO;
		BigDecimal currentChildValue = getObjectPropertyAsBigDecimal(currentChild, summedField);
		BigDecimal priorChildValue = getObjectPropertyAsBigDecimal(priorChild, summedField);

		// If this is a change of parent, does the child qualify?
		if ( ! oldAndNewParentsAreEqual) {
			if (currQual)
				currentAdjustBy = currentChildValue;
		}
		else { // The parent is the same
			currentAdjustBy = currentChildValue.subtract(priorChildValue);

			// Did the qualification change? If it did, the adjustment is the whole value.
			if (priorQual && !currQual) {
				currentAdjustBy = priorChildValue.negate();
			}
			else if (!priorQual && currQual)
				currentAdjustBy = currentChildValue;
		}

		// If there is something to adjust, do it
		if ( currentAdjustBy.compareTo(BigDecimal.ZERO) != 0) {
			if ( ! currentCascadeDeferred)
				aParentAdjustments.setOldAdjustedParentDomainObject(currentParentPersBean);
			BigDecimal currentParentValue = BigDecimal.ZERO;
			
			// For transient attribute, we just reset them to null, and they'll get 
			// recomputed when needed.
			if (metaAttribute.isTransient()) {
				currentParentPersBean.put(getBeanAttributeName(), null);
			}
			else {
				currentParentValue = getObjectPropertyAsBigDecimal(currentParentPersBean, getBeanAttributeName());
				if (currentParentValue == null)
					currentParentValue = BigDecimal.ZERO;
				BigDecimal currentParentNewValue = currentParentValue.add(currentAdjustBy);
				Number theNewNewSum = NumberUtil.convertNumberToType(currentParentNewValue, attType);
				currentParentPersBean.put(getBeanAttributeName(), theNewNewSum);
			}

			if (log.isInfoEnabled())  {
				String parentClassNameDB = NodalPathUtil.getNodalPathLastName(parentMetaEntity.getEntityName());
				log.debug ("Adjusting sum " +  parentClassNameDB +
						"." + getBeanAttributeName() + "-=" + currentAdjustBy + " from deleted child", childLogicRunner );
			}

			if ( ! noCode)
				invokeLogicMethod(currentParentPersBean, null, childLogicRunner);
			if ( ! currentCascadeDeferred)
				aParentAdjustments.setAdjustedParentDomainObject (currentParentPersBean);  // cause it to be saved

			firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
					aParentAdjustments.getChildLogicRunner(), currentParentPersBean, currentParentValue, 
					System.nanoTime() - startTime);
		}
	}


	private void adjustFromDeletedChild(AdjustAllParents aParentAdjustments) {
		if ( ! isPersistent()) {
			if (log.isDebugEnabled())
				log.debug("Adjustment not required for deleted child for *transient* attribute: " + 
						getBeanAttributeName());
			return;
		}
		
		long startTime = System.nanoTime();
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();
		BigDecimal currSummedD;
		
		// adjust new by delta (if any) - TODO - other algorithms
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		//PersistentBean originalChild = context.getDeletedObjectState(currentChild);
		boolean currQual = runQualificationForBean(currentChild);  	//todo - can possibly prune per dependsOn roles (needs thought)
		if (currQual ==  false)
			return; // not qualified - no adjustment
		currSummedD = getObjectPropertyAsBigDecimal(currentChild, summedField);
		BigDecimal adjustment = BigDecimal.ZERO.subtract(currSummedD);

		if ( ! role.isCollection())
			throw new RuntimeException("Sum cannot be defined on a non-collection relationship: " + role);
		String roleToParent = role.getOtherMetaRole().getRoleName();
		Object parent = currentChild.get(roleToParent);
		PersistentBean theParentState = null;
		boolean cascadeDeferred = false;
		
		if (parent != null) {
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
			theParentState = HibPersistentBeanFactory.getInstance(context.getSession()).
					createPersistentBeanFromObject(parent, ep);

			// Look ahead in the LogicRunner queue for a LogicRunner for the
			// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
			LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(theParentState);
			if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
				if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
					theParentState = aheadLogicRunner.getCurrentDomainObject();
					cascadeDeferred = true;
					if (log.isInfoEnabled())
						log.info("Cascade of parent update (from sum delete) will be deferred since the parent object is already scheduled for processing");
				}
			}
		}
		
		if (parent != null && ! context.objectIsDeleted(theParentState)) {
			BigDecimal theSum = BigDecimal.ZERO;
			
			// For transient attribute, we just reset them to null, and they'll get 
			// recomputed when needed.
			MetaAttribute metaAttribute = logicGroup.getMetaEntity().getMetaAttribute(getBeanAttributeName());
			if (metaAttribute.isTransient()) {
				theParentState.put(getBeanAttributeName(), null);
			}
			else {
				if ( ! cascadeDeferred)
					aParentAdjustments.setOldAdjustedParentDomainObject (theParentState);
				theSum = getObjectPropertyAsBigDecimal(theParentState, getBeanAttributeName());
				if (theSum == null) 
					theSum = BigDecimal.ZERO;  // no need to initialize aggregates
				BigDecimal theNewSum = theSum.add(adjustment);
				Class<?> attType = theParentState.getMetaEntity().getMetaAttribute(getBeanAttributeName()).getType();
				Number theNewNewSum = NumberUtil.convertNumberToType(theNewSum, attType);
				theParentState.put(getBeanAttributeName(), theNewNewSum);
			}
			if (log.isInfoEnabled())
				log.debug ("Adjusting sum " +  theParentState.getClass().getSimpleName() +
						"." + getBeanAttributeName() + "-=" + adjustment + " from updated/reparented child", childLogicRunner );
			if ( ! cascadeDeferred)
				aParentAdjustments.setAdjustedParentDomainObject (theParentState);  // cause it to be saved

			if ( ! noCode)
				invokeLogicMethod(theParentState, null, childLogicRunner);

			firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
					aParentAdjustments.getChildLogicRunner(), theParentState, theSum, System.nanoTime() - startTime);
		}
	}

	/**
	 * General shape of query:
	 * <br><br> 
	 * SELECT SUM(Amount) FROM Lineitem<br>
	 * WHERE PurchaseOrderNumber IN<br>
	 * (SELECT OrderNumber FROM Purchaseorder<br>
	 * WHERE CustomerName="Gloria's Garden" and isReady = false) and QtyOrdered > 0;
	 *
	 * @param aParentObjectState
	 * @param aContext
	 * return sum, using sql select
	 */
//	public void computeAggregateFromDatabase(Object bean, LogicTransactionContext aContext) {
//		if (dependsOnTransientWhere() )
//			computeAggregateInMemory(bean, aContext);
//		else
//			throw new LogicException("Sum from SQL coming soon");
//	}

	/**
	 * 
	 * @return true if transients in where clause
	 */
//	private boolean dependsOnTransientWhere() {
//		return true;
//	}


	//private static boolean proxyCallsForValue = false;  // interim scaffolding until implemented



	/**
	 * Mechanism to initialize sum attribute, e.g., from Domain Object getter.
	 * 
	 * @param aParentDomainObject Domain Object containing the sum
	 * @param anAttributeName name of sum attribute
	 * @param aSessionFactory non EE may require global static, EE can use sf = (SessionFactory)new InitialContext().lookup("MySessionFactory")
	 * @return
	 */
/* This is only invoked for non-persistent attributes so I am taking it out for now
	public static BigDecimal getSumValue(Object aParentDomainObject, String anAttributeName, SessionFactory aSessionFactory) {
		SumRule sumRule = null;
		Session session = aSessionFactory.getCurrentSession(); //   aContext.getSession();
		Transaction transaction = session.getTransaction();
		LogicTransactionContext context = LogicTransactionManager.getCurrentLogicTransactionContextForTransaction(transaction, session);
		// ObjectState parentState = ObjectStateFactory.createObjectStateForObject(aParentDomainObject, session);  // TODO:  how does bean get session??
		PersistentEntity entity = 
		List<RoleMeta> childRoles = RoleMeta.findChildrenRoles(aParentDomainObject.getClass(), context);
		for (RoleMeta eachChildRole: childRoles) {
			Set <AbstractAggregateRule> aggregates = RuleMeta.findAggregatesForRole (eachChildRole);
			for (AbstractAggregateRule eachAggregate: aggregates) {
				if (eachAggregate.getBeanAttributeName().equalsIgnoreCase(anAttributeName)) {
					sumRule = (SumRule) eachAggregate;
					break;
				}
			}
			if (sumRule != null)
				break;
		}
		if (sumRule == null)
			throw new LogicException("No Sum definition found for " + anAttributeName);

//		ObjectState theParentState = ObjectStateFactory.createObjectStateForObject(aParentDomainObject, session);
		return sumRule.computeSumInMemory(aParentDomainObject, context); 
	}
*/

	/**
	 * Called when adjustment notes null, or proxy getter notes null, to set pojo attribute value.
	 * 
	 * @return old value, but deletions not counted (hmm, useful for apps?)
	 */
	public BigDecimal computeSumInMemory(Object bean, LogicTransactionContext aContext) {
		
		BeanMap beanMap = new BeanMap(bean);
		BigDecimal resultNumber = BigDecimal.ZERO;
		Collection<?> theChildren = (Collection<?>)beanMap.get(roleName);
		
		if (theChildren == null || theChildren.isEmpty())
			return BigDecimal.ZERO;
		
		PersistenceContext persistenceContext = HibernateSessionUtil.getPersistenceContextForSession(aContext.getSession());
		if ( ! persistenceContext.containsCollection((PersistentCollection)theChildren)) {
			String errMsg = "Persistent collection " + roleName + " of object " + bean + " is not in the current transaction. This usually means " +
			"that you are trying to use a transient sum or count in a commit action or commit constraint, and the object in question is being deleted. " +
			"You should change your constraint or action (in this case, " + getLogicGroup().getLogicClassName() + "." + getLogicMethodName() +
			") so that it does not fire when the object is being deleted.";
			throw new RuntimeException(errMsg);
		}
		
/* Note : I am commenting this out until we have a better idea of how PersistentBeans are handled
		for (Object eachChild: theChildren) {
			ObjectState childObjectState = ObjectStateFactory.createObjectStateForObject(eachChild, aContext.getSession());
			if (childObjectState == null) // This usually means the child has not yet been saved, and therefore has no pk yet. We'll get to it later.
				return null;
			
			// Always compute value from scratch -- we'll refine this to real adjustments at some point in the future.
			BigDecimal summedValue2 = getObjectPropertyAsBigDecimal(childObjectState, this.summedField);
			boolean childMeetsQualificationCondition2 = runQualificationForBean(childObjectState);
			if (childMeetsQualificationCondition2)
				resultNumber = resultNumber.add(summedValue2);
*/
			/** This code is turned off until we're ready to try real adjustments 
			// We want to use the old values to add up because the new values will result in an adjustment later on.
			 
			ObjectState oldChildObjectState = childObjectState;
			String fromQualifiedChild = " from qualified child ";
			LogicRunner childLogicRunner = aContext.findLogicRunner(childObjectState);
			if (childLogicRunner != null) {  // deletes do not show up here, so are skipped for adjustment of transients
				fromQualifiedChild = " from Qualified <" + childLogicRunner.getLogicProcessingState() + "> child ";
				if (childLogicRunner.getPriorDomainObject() == null)
					oldChildObjectState = null;	// it's an insert - we'll process that later, and it does not contribute to the old sum
				else
					oldChildObjectState = childLogicRunner.getPriorDomainObject();
			}
			
			if (oldChildObjectState == null)
				continue;
			
			// Of course, this only makes sense if the old value actually belonged to this parent.
			// We compare the primary keys because the objects themselves are often different, i.e. the
			// one retrieved from the old ObjectState is typically a proxy.
			Object oldParent = oldChildObjectState.get(parentRole);
			if (oldParent == null)
				continue;
			
			Object oldParentPk = aContext.getSession().getIdentifier(oldParent);
			if (oldParentPk == null)
				continue;
			Object beanPk = aContext.getSession().getIdentifier(bean);
			if ( ! oldParentPk.equals(beanPk))
				continue;
			
			BigDecimal summedValue = getObjectPropertyAsBigDecimal(oldChildObjectState, this.summedField);
			boolean childMeetsQualificationCondition = runQualificationForBean(childObjectState);
			if (childMeetsQualificationCondition) {
				resultNumber = resultNumber.add(summedValue);

			if (log.isDebugEnabled()) 
				log.debug("InMem["  + getBeanAttributeName() + "]+="+ resultNumber + fromQualifiedChild + childObjectState.toString(null, aContext.getSession()));
			}
			*/
//		} // for eachChild
		
		if (log.isInfoEnabled())
			log.info ("InMem[" + getBeanAttributeName() + "]==" + resultNumber + 
					" in parent " + beanMap.toString());

		return resultNumber;		
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Sum " + getLogicGroup().getMetaEntity().getEntityName() + "#" + 
				this.getLogicMethodName()+ ", summing: " + summedField + " over role " + this.getRoleName());
		if (getQualification() != null && getQualification().trim().length() > 0)
			sb.append(" where " + this.getQualification());
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  SumRule.java 1303 2012-04-28 00:16:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 