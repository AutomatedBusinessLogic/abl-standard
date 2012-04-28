package com.autobizlogic.abl.rule;

import java.math.BigDecimal;

import org.hibernate.Query;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.engine.phase.AdjustAllParents;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.engine.LogicRunner.LogicProcessingState;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.hibernate.HibMetaEntity;
import com.autobizlogic.abl.session.LogicTransactionContext;
//import com.autobizlogic.abl.util.BeanUtil;
//import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.NumberUtil;
import com.autobizlogic.abl.util.ObjectUtil;

public class MinMaxRule extends AbstractAggregateRule {

	public enum MinMaxType { MIN, MAX }
	private MinMaxType type;
	private String watchedField;

	protected MinMaxRule(LogicGroup logicGroup, String logicMethodName, String roleName, String clause, 
			String watchedField, String beanAttributeName, MinMaxType type) {
		super(logicGroup, logicMethodName, roleName, clause, beanAttributeName);
		this.watchedField = watchedField;
		this.type = type;
	}

	/**
	 * Get the name of the child attribute that is watched up by this rule.
	 */
	public String getWatchedFieldName() {
		return watchedField;
	}
	
	public MinMaxType getType() {
		return type;
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

	/**
	 * Several scenarios possible:
	 * <ol>
	 * <li>inserted value is null : no effect
	 * <li>current parent value is null: becomes child value
	 * <li>inserted value is more/less than parent value: set parent value to it
	 * </ol>
	 * @param aParentAdjustments
	 */
	private void adjustFromInsertedChild(AdjustAllParents aParentAdjustments) {

		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		boolean currQual = runQualificationForBean(currentChild);
		if (currQual ==  false)
			return;  // not qualified: no adjustment
		BigDecimal currChildValue = getObjectPropertyAsBigDecimal(currentChild, watchedField, true);
		if (currChildValue == null) // Child has null value: does not affect parent
			return;

		long startTime = System.nanoTime();
		LogicTransactionContext context = childLogicRunner.getContext();
		
		if ( ! getRole().isCollection())
			throw new RuntimeException("Cannot do min/max over a non-collection relationship: " + getRole());
		String parentRoleName = getRole().getOtherMetaRole().getRoleName();
		Object theParent = currentChild.get(parentRoleName);
		if (theParent == null)
			return;
		
		PersistentBean parentBean = null;
		
		HibMetaEntity otherMetaEntity = (HibMetaEntity)role.getMetaEntity();
		if (theParent instanceof PersistentBean)
			parentBean = (PersistentBean)theParent;
		else
			parentBean = HibPersistentBeanFactory.getInstance(context.getSession()).
				createPersistentBeanFromObject(theParent, otherMetaEntity.getEntityPersister());
		
		BigDecimal currentMinMax = null;

		// For transient attributes, we just set them to null so that they are recomputed when needed
		MetaAttribute metaAttribute = getLogicGroup().getMetaEntity().getMetaAttribute(getBeanAttributeName());
		if (metaAttribute.isTransient()) {
			parentBean.put(getBeanAttributeName(), null);
			return;
		}
		
		// If the current min/max value is not null, does it need to be changed?
		currentMinMax = getObjectPropertyAsBigDecimal(parentBean, getBeanAttributeName(), true);
		if (currentMinMax != null) {
		
			// If the child value is less than the max or more than the min, we're done
			if ((type == MinMaxType.MIN && currChildValue.compareTo(currentMinMax) >= 0) ||
					(type == MinMaxType.MAX && currChildValue.compareTo(currentMinMax) <= 0)) {
				return;
			}
		}
		
		// At this point, we know that the min/max is going to change
		// Look ahead in the LogicRunner queue for a LogicRunner for the
		// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
		boolean cascadeDeferred = false;
		LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(parentBean);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				parentBean = aheadLogicRunner.getCurrentDomainObject();
				cascadeDeferred = true;
				if (log.isDebugEnabled())
					log.debug("Cascade of parent update (from min/max insert) will be deferred since the parent object is already scheduled for processing");
			}
		}

		if ( ! cascadeDeferred)
			aParentAdjustments.setOldAdjustedParentDomainObject(parentBean);

		Class<?> attType = parentBean.getMetaEntity().getMetaAttribute(getBeanAttributeName()).getType();
		Number newMinMax = NumberUtil.convertNumberToType(currChildValue, attType);
		parentBean.put(getBeanAttributeName(), newMinMax);
		
		if (log.isDebugEnabled())
			log.debug ("Adjusting min/max " +  theParent.getClass().getSimpleName() +
					"." + getBeanAttributeName() + "=" + newMinMax + " from child", childLogicRunner );

		if ( ! noCode)
			invokeLogicMethod(parentBean, null, childLogicRunner);

		firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
				aParentAdjustments.getChildLogicRunner(), parentBean, newMinMax, System.nanoTime() - startTime);

		if ( ! cascadeDeferred)
			aParentAdjustments.setAdjustedParentDomainObject(parentBean);  // cause it to be saved
	}

	/**
	 * An updated child is far more complex than an inserted child, so take a deep breath.
	 * Several things can happen here:
	 * <ol>
	 * <li>the child may have changed parent
	 * <li>the child's qualification may have changed
	 * <li>the watched value may have changed
	 * <li>if it has changed, whether or not it is beyond the current min/max
	 * </ol>
	 * All the possible combinations have to be taken into account. Thankfully, many combinations
	 * result in no action. For instance, if the watched value was null and is still null, or if
	 * both the old child and the new child are not qualified.
	 * @param aParentAdjustments
	 */
	private void adjustFromUpdatedChild(AdjustAllParents aParentAdjustments) {

		long startTime = System.nanoTime();
		LogicRunner childLogicRunner = aParentAdjustments.getChildLogicRunner();
		LogicTransactionContext context = childLogicRunner.getContext();

		PersistentBean priorChild = childLogicRunner.getPriorDomainObject();
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();

		// If the watched value was null and is still null, we're done
		BigDecimal oldChildValue = getObjectPropertyAsBigDecimal(priorChild, watchedField, true);
		BigDecimal currentChildValue = getObjectPropertyAsBigDecimal(currentChild, watchedField, true);
		if (oldChildValue == null && currentChildValue == null)
			return;

		// Determine whether the child used to be our child (this does not issue SQLs)
		String roleToParentName = getRole().getOtherMetaRole().getRoleName();
		Object priorParent = priorChild.get(roleToParentName);
		Object currentParent = currentChild.get(roleToParentName);

		// If there was no parent, and there still isn't, we are done
		if (priorParent == null && currentParent == null)
			return;

		boolean priorCascadeDeferred = false;
		boolean currentCascadeDeferred = false;
		HibMetaEntity parentMetaEntity = (HibMetaEntity)role.getMetaEntity();
		
		if (priorParent instanceof PersistentBean)
			throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
		
		PersistentBean priorParentPersBean = HibPersistentBeanFactory.getInstance(context.getSession()).
			createPersistentBeanFromObject(priorParent, parentMetaEntity.getEntityPersister());
		
		if (priorParent instanceof PersistentBean)
			throw new LogicException("Unexpected Persistent Bean - expected map/pojo");
		
		PersistentBean currentParentPersBean = HibPersistentBeanFactory.getInstance(context.getSession()).
			createPersistentBeanFromObject(currentParent, parentMetaEntity.getEntityPersister());
		
		// Look ahead in the LogicRunner queue for a LogicRunner for the
		// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
		LogicRunner aheadPriorLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(priorParentPersBean);
		if (aheadPriorLogicRunner != null && aheadPriorLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadPriorLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				priorParentPersBean = aheadPriorLogicRunner.getCurrentDomainObject();
				priorCascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade of old parent update (from min/max update) will be deferred " + 
							"since the old parent object is already scheduled for processing");
			}
		}
		LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(currentParentPersBean);
		if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
			if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
				currentParentPersBean = aheadLogicRunner.getCurrentDomainObject();
				currentCascadeDeferred = true;
				if (log.isInfoEnabled())
					log.info("Cascade of current parent update (from min/max update) will be deferred " + 
							"since the current parent object is already scheduled for processing");
			}
		}

		// There are three dimensions to keep in mind here:
		// - the child may have changed parent (none -> one, one -> none, one -> different one)
		// - the watched attribute may have changed value
		// - the qualification condition may have changed

		// Start by determining whether the old and new child objects satisfy the condition
		boolean priorQual = runQualificationForBean(priorChild);
		boolean currQual = runQualificationForBean(currentChild);

		// If the child was not part of the equation in the old state, and it still isn't, we're done
		if (!priorQual && !currQual)
			return;

		// If the parent has not changed, and the watched value has not changed,
		// and the qualification has not changed, then the min/max clearly does not change
		if (BeanUtil.beansAreEqual(parentMetaEntity, priorParent, currentParent) &&
				ObjectUtil.objectsAreEqual(oldChildValue, currentChildValue) &&
				(priorQual && currQual)) {
			return;
		}

		// Determine the type of the result attribute
		MetaAttribute metaAttribute = logicGroup.getMetaEntity().getMetaAttribute(getBeanAttributeName());
		Class<?> attType = metaAttribute.getType();

		boolean oldAndNewParentsAreEqual = BeanUtil.beansAreEqual(parentMetaEntity, priorParent, currentParent);

		// Start with taking care of the old parent, if any
		// This is done only if:
		// 1 - There was an old parent
		// 2 - The old parent is not the same as the new parent
		// 3 - The child qualified in the old state
		// 4 - The old state was not null
		if (priorParent != null && !oldAndNewParentsAreEqual && priorQual && oldChildValue != null) { 
			// we are definitely adjusting a prior parent - need its old values
			// the above should not require a getParent sql, but we need all the values now (fetched below)
			PersistentBean priorParentOldValues = priorParentPersBean.duplicate();

			BigDecimal oldParentValue = getObjectPropertyAsBigDecimal(priorParentPersBean, getBeanAttributeName(), true);
			boolean oldParentNeedsRecompute = false;
			if (oldParentValue != null) { // oldParentValue should never be null here, but just in case
				oldParentNeedsRecompute = valueOutside(oldParentValue, oldChildValue);
			}
			
			if (oldParentNeedsRecompute) {
				// For transient attribute, we just reset them to null, and they'll get 
				// recomputed when needed.
				if (metaAttribute.isTransient()) {
					priorParentPersBean.put(getBeanAttributeName(), null);
				}
				else {
					sqlRecompute(priorParentPersBean, context);
				}
				firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
						aParentAdjustments.getChildLogicRunner(), priorParentPersBean, 
						oldParentValue, System.nanoTime() - startTime);
				startTime = System.nanoTime(); // Restart the clock
			}

			if (log.isDebugEnabled())  {
				String parentClassNameDB = NodalPathUtil.getNodalPathLastName(parentMetaEntity.getEntityName());
				log.debug ("Adjusting min/max " +  parentClassNameDB +
						"." + getBeanAttributeName() + "=" + 
						getObjectPropertyAsBigDecimal(priorParentPersBean, getBeanAttributeName(), true) + 
						" from reparented child", childLogicRunner );
			}

			if ( ! noCode)
				invokeLogicMethod(priorParentPersBean, null, childLogicRunner);

			if ( ! priorCascadeDeferred) {
				aParentAdjustments.setPriorAdjustedParentDomainObject (priorParentPersBean);  // cause it to be saved
				aParentAdjustments.setPriorOldAdjustedParentDomainObject(priorParentOldValues);
			}

		}  	// end reparenting - take care of prior parent

		// Obviously if there is no current parent, we're done
		if (currentParent == null)
			return;
		
		// We now determine whether the current parent needs to be recomputed
		BigDecimal currentParentValue = getObjectPropertyAsBigDecimal(currentParentPersBean, getBeanAttributeName(), true);
		BigDecimal newParentValue = null;
		boolean newParentValueNeedsRecomputing = false;
		
		if (oldAndNewParentsAreEqual) { // The parent is the same

			// Did the qualification change?
			if (priorQual && currQual) {
				// We're only interested if the child value has changed
				if ( ! ObjectUtil.objectsAreEqual(oldChildValue, currentChildValue)) {
					if (currentChildValue == null)
						newParentValueNeedsRecomputing = valueOutside(currentParentValue, oldChildValue);
					else {
						if (valueOutside(currentParentValue, currentChildValue))
							newParentValue = currentChildValue;
						else
							newParentValueNeedsRecomputing = valueOutside(currentParentValue, oldChildValue);
					}
				}
			}
			else if (priorQual && !currQual) {
				if (oldChildValue != null)
					newParentValueNeedsRecomputing = valueOutside(currentParentValue, oldChildValue);
			}
			else if (!priorQual && currQual) {
				if (currentChildValue != null) {
					if (valueOutside(currentParentValue, currentChildValue))
						newParentValue = currentChildValue;
				}
			}
		}
		else if (priorParent == null) { // We went from a null parent to a non-null parent
			if (currQual) { // If we don't qualify, there is nothing to do
				if (valueOutside(currentParentValue, currentChildValue))
					newParentValue = currentChildValue;
			}
		}
		else { // We got a different parent
			if (currQual) {
				if (oldChildValue == null) {
					if (valueOutside(currentParentValue, currentChildValue))
						newParentValue = currentChildValue;
				}
				else if (currentChildValue == null) {
					newParentValueNeedsRecomputing = valueOutside(currentParentValue, oldChildValue);
				}
				else  {
					if (valueOutside(currentParentValue, currentChildValue))
						newParentValue = currentChildValue;
					else
						newParentValueNeedsRecomputing = valueOutside(currentParentValue, oldChildValue);
				}
			}
		}
		
		// If the parent value needs to be updated, do it now
		if (newParentValueNeedsRecomputing || newParentValue != null) {
			if (newParentValueNeedsRecomputing) {
				sqlRecompute(currentParentPersBean, context);
			}
			else if (newParentValue != null) {
				Number n = NumberUtil.convertNumberToType(newParentValue, attType);
				currentParentPersBean.put(getBeanAttributeName(), n);
			}

			if (log.isInfoEnabled())  {
				String parentClassNameDB = NodalPathUtil.getNodalPathLastName(parentMetaEntity.getEntityName());
				log.debug ("Adjusting min/max " +  parentClassNameDB +
						"." + getBeanAttributeName() + "=" + 
						getObjectPropertyAsBigDecimal(priorParentPersBean, getBeanAttributeName(), true) + 
						" from updated/reparented child", childLogicRunner );
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

	/**
	 * Determine whether the given value is outside of the parentValue (given that we are either
	 * a min or max).
	 * @param parentValue The reference value
	 * @param value The value to compare to parentValue
	 * @return If this is a MIN, return value &lt;= parentValue, if this is a MAX, return value >= parentValue
	 */
	private boolean valueOutside(BigDecimal parentValue, BigDecimal value) {
		if (parentValue == null)
			return true;
		if (type.equals(MinMaxType.MIN) && value.compareTo(parentValue) <= 0)
			return true;
		if (type.equals(MinMaxType.MAX) && value.compareTo(parentValue) >= 0)
			return true;	
		return false;
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
		
		PersistentBean currentChild = childLogicRunner.getCurrentDomainObject();
		boolean currQual = runQualificationForBean(currentChild);
		if (currQual ==  false)
			return; // Bean was not not qualified - no effect
		BigDecimal currChildValue = getObjectPropertyAsBigDecimal(currentChild, watchedField, true);
		if (currChildValue == null) // Null does not affect the min/max in any way
			return;

		if ( ! role.isCollection())
			throw new RuntimeException("Min/max cannot be defined on a non-collection relationship: " + role);
		String roleToParent = role.getOtherMetaRole().getRoleName();
		Object parent = currentChild.get(roleToParent);
		PersistentBean theParentState = null;
		boolean cascadeDeferred = false;
		
		if (parent != null) {
			HibMetaEntity parentMetaEntity = (HibMetaEntity)role.getMetaEntity();
			theParentState = HibPersistentBeanFactory.getInstance(context.getSession()).
					createPersistentBeanFromObject(parent, parentMetaEntity.getEntityPersister());

			// Look ahead in the LogicRunner queue for a LogicRunner for the
			// parent object. If there is one, we're going to adjust its current bean and NOT cascade.
			LogicRunner aheadLogicRunner = aParentAdjustments.getChildLogicRunner().getContext().findLogicRunner(theParentState);
			if (aheadLogicRunner != null && aheadLogicRunner.getLogicProcessingState() != LogicProcessingState.COMPLETED) {
				if (aheadLogicRunner.getExecutionState() != LogicRunnerPhase.ACTIONS) {
					theParentState = aheadLogicRunner.getCurrentDomainObject();
					cascadeDeferred = true;
					if (log.isInfoEnabled())
						log.info("Cascade of parent update (from min/max delete) will be deferred since the parent object is already scheduled for processing");
				}
			}
		}
		
		if (parent != null && ! context.objectIsDeleted(theParentState)) {
			
			// For transient attribute, we just reset them to null, and they'll get 
			// recomputed when needed.
			MetaAttribute metaAttribute = logicGroup.getMetaEntity().getMetaAttribute(getBeanAttributeName());
			if (metaAttribute.isTransient()) {
				theParentState.put(getBeanAttributeName(), null);
			}
			else {
				if ( ! cascadeDeferred)
					aParentAdjustments.setOldAdjustedParentDomainObject (theParentState);
				BigDecimal parentMinMax = getObjectPropertyAsBigDecimal(theParentState, getBeanAttributeName(), true);
				if (parentMinMax == null) {
					log.warn("The value for min/max " + this.toString() + " is null for " + theParentState + 
							". This is unexpected because the deleted child value was not null.");
					parentMinMax = currChildValue;
				}
				
				// If the deleted value does not affect the min/max, we're done
				if ((type == MinMaxType.MIN && currChildValue.compareTo(parentMinMax) > 0) ||
						(type == MinMaxType.MAX && currChildValue.compareTo(parentMinMax) < 0)) {
					return;
				}
				sqlRecompute(theParentState, context);
			}
			if (log.isInfoEnabled())
				log.debug ("Adjusting min/max " +  theParentState.getClass().getSimpleName() +
						"." + getBeanAttributeName() + "=" + theParentState.get(getBeanAttributeName()) + 
						" from deleted child", childLogicRunner );
			if ( ! cascadeDeferred)
				aParentAdjustments.setAdjustedParentDomainObject (theParentState);  // cause it to be saved

			if ( ! noCode)
				invokeLogicMethod(theParentState, null, childLogicRunner);

			firePostEvent(aParentAdjustments.getChildLogicRunner().getLogicObject(), 
					aParentAdjustments.getChildLogicRunner(), theParentState, 
					(Number)theParentState.get(getBeanAttributeName()), System.nanoTime() - startTime);
		}
	}
	
	/**
	 * When there is no other way, we have to issue a SQL to figure out the new min/max value.
	 */
	/*private*/ void sqlRecompute(PersistentBean bean, LogicTransactionContext context) {
		
		String minMax = "max";
		if (type == MinMaxType.MIN)
			minMax = "min";
		String sql = "select " + minMax + "(" + getWatchedFieldName() + ") from " + 
			getRole().getOtherMetaEntity().getEntityName() + " where " + 
			getRole().getOtherMetaRole().getRoleName() + " = :parent";
		if (getQualificationSQL() != null)
			sql += " and (" + getQualificationSQL() + ")";
		Query query = context.getSession().createQuery(sql);
		query.setEntity("parent", bean.getEntity());
		Object result = query.uniqueResult();
		Class<?> attType = bean.getMetaEntity().getMetaAttribute(getBeanAttributeName()).getType();
		Number newMax = NumberUtil.convertNumberToType((Number)result, attType);
		bean.put(getBeanAttributeName(), newMax);
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Mundane stuff

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		String typeStr = "Maximum";
		if (type == MinMaxType.MIN)
			typeStr = "Minimum";
		sb.append( typeStr + " " + getLogicGroup().getMetaEntity().getEntityName() + "#" + 
				this.getLogicMethodName()+ ", for: " + watchedField + " over role " + this.getRoleName());
		if (getQualification() != null && getQualification().trim().length() > 0)
			sb.append(" where " + this.getQualification());
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  MinMaxRule.java 1012 2012-03-27 09:20:06Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 