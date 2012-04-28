package com.autobizlogic.abl.logic;

import java.math.BigDecimal;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.Type;

import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.hibernate.HibernateUtil;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.BeanMap;

/**
 * Common rule services.
 */
public class LogicSvcs {

	static boolean isFormulaPruningEnabled = true; // set to false to short-circuit   TODO - reparent check

	/**
	 * Determine whether the given attribute has changed between the current state and the old state.
	 * @param aChildLogicRunner
	 * @param anAttributeName
	 * @return true if anAttributeName's value changed from aChildLogicRunner's current/priorDomainObject
	 */
	public static boolean isAttributeChanged(LogicRunner aChildLogicRunner, String anAttributeName) {
		PersistentBean priorDomainObject = aChildLogicRunner.getPriorDomainObject();
		if (priorDomainObject == null) {
			throw new RuntimeException("Attempted to compare old and new state, but there is no old state.");
		}
		Object oldValue = priorDomainObject.get(anAttributeName);
		
		PersistentBean currentDomainObject = aChildLogicRunner.getCurrentDomainObject();
		if (currentDomainObject == null) {
			throw new RuntimeException("Attempted to compare old and new state, but there is no new state.");
		}
		Object currentValue = currentDomainObject.get(anAttributeName);
		
		if (currentValue == null && oldValue == null)
			return false;
		
		if (currentValue == null || oldValue == null)
			return true;
		
		return ! currentValue.equals(oldValue);
	}

	
	/**
	 * 
	 * @param aRoleMeta  eg, purchaseorder
	 * @param aChildRunner
	 * @return true if parent is propagating changes to Parent References along aRoleMeta
	 */
	public static boolean isParentCascadingChangedRefdAttrs(MetaRole role, LogicRunner aChildRunner) {
		boolean rtnBoolean = false;
		// FIXME childrenRoleMeta is what we need
		if (role.equals(aChildRunner.getCallingRole()))  // not testing logicSource -- could be Deferred Cascade (see CascadeParentReferences)
			rtnBoolean = true;
		return rtnBoolean;
	}


	/**
	 * 
	 * @param aSourceHibernateBean
	 * @param aLogicRunner - just for context
	 * @return 2nd instance of aSourceHibernateBean, with non-collection properties
	 */
	public static Object beanCopyOf(Object aSourceHibernateBean, LogicRunner aLogicRunner) {
		Object rtnTargetHibernateBean = null;
		LogicTransactionContext context = aLogicRunner.getContext();

		BigDecimal defaultValue = null;  //new BigDecimal("0.0");  // an experiment
		Converter bdc = new BigDecimalConverter(defaultValue);
		ConvertUtils.register(bdc, BigDecimal.class);

		Class<?> hibernateDomainBeanClass = HibernateUtil.getEntityClassForBean(aSourceHibernateBean);
		ClassMetadata entityMeta = context.getSession().getSessionFactory()
				.getClassMetadata(hibernateDomainBeanClass);			
		Type propertyTypes[] = entityMeta.getPropertyTypes();
		String propertyNames[] = entityMeta.getPropertyNames(); // hope names/types share index...

		String className = hibernateDomainBeanClass.getName();
		try {
			rtnTargetHibernateBean = ClassLoaderManager.getInstance().getClassFromName(className).newInstance();
		} catch (Exception e) {
			throw new LogicException("Unable to instantatiate new Bean like: " + aSourceHibernateBean, e);
		}
		BeanMap rtnBeanMap = new BeanMap(rtnTargetHibernateBean);
		BeanMap srcBeanMap = new BeanMap(aSourceHibernateBean);
		Object eachValue = null;
		for (int i = 0; i < propertyNames.length; i++) {
			String propertyName = propertyNames[i];		
			try {
				Type type = propertyTypes[i];
				if ( ! type.isCollectionType()  && ! type.isEntityType()) {  // NB - not moving collections!
					eachValue = srcBeanMap.get(propertyName);
					rtnBeanMap.put(propertyName, eachValue);
				} else { 
					BeanUtils.setProperty(rtnTargetHibernateBean, propertyNames[i], null);	// prevent ClassCastException: HashSet cannot be cast to PersistentCollection
				}
			} catch (Exception e) {
				throw new LogicException("Cannot set Property: " + propertyNames[i] + " = " + eachValue + ", on " + rtnTargetHibernateBean);
			}
		}
		// TODO - IMPORTANT - set the key field(s), presumably using Hibernate meta data		
		return rtnTargetHibernateBean;
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
 