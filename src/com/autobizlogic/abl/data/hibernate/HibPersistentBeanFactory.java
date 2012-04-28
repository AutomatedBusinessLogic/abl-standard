package com.autobizlogic.abl.data.hibernate;

import java.io.Serializable;
import java.util.Map;

import org.hibernate.EntityMode;
import org.hibernate.Session;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.metadata.hibernate.HibMetaEntity;
import com.autobizlogic.abl.util.ClassNameUtil;

/**
 * The class responsible for creating instances of HibPersistentBean and HibPersistentBeanCopy.
 */
public class HibPersistentBeanFactory {

	private Session session;
	
	private HibPersistentBeanFactory(Session session) {
		this.session = session;
	}
	
	/**
	 * Get an instance for the given session.
	 */
	public static HibPersistentBeanFactory getInstance(Session session) {
		return new HibPersistentBeanFactory(session);
	}
	
	/**
	 * Create a PersistentBean from a Pojo or Map entity.
	 */
	public PersistentBean createPersistentBeanFromObject(Object bean, EntityPersister ep) {
		if (bean == null)
			return null;
		if (bean instanceof PersistentBean)
			throw new RuntimeException("Cannot create a PersistentBean from a PersistentBean: " + bean);
		if ( ! (bean instanceof Map)) {
			Class<?> epCls = ep.getMappedClass(EntityMode.POJO);
			Class<?> beanCls = bean.getClass();
			if ( ! epCls.isAssignableFrom(beanCls))
				throw new RuntimeException("Bean is of wrong type for the given persister: " + beanCls.getName() +
						" vs " + epCls.getName());
		}
		Serializable pk;
		try {
			pk = ep.getIdentifier(bean, (SessionImplementor)session);
		}
		catch(Exception ex) {
			throw new RuntimeException(ex);
		}
		return new HibPersistentBean(bean, pk, ep, session);
	}
	
	/**
	 * Make a shallow copy of the given Pojo or Map bean. 
	 * The resulting PersistentBean will contain a copy
	 * of all the attributes and single-valued relationships. Access to the collections will
	 * be deferred to the original bean passed here.
	 */
	public static PersistentBean createPersistentBeanCopyFromEntity(PersistentBean bean, EntityPersister ep) {
		if (bean == null)
			return null;
		return new HibPersistentBeanCopy(bean, bean.getPk(), ep);
	}
	
	/**
	 * Create a PersistentBean from a bean (either Pojo or Map).
	 * @param bean The bean
	 * @param entityName Required if the bean is a Map
	 * @param session The Hibernate session
	 * @return The newly minted PersistentBean
	 */
	public PersistentBean createPersistentBeanFromEntity(Object bean, String entityName) {
		if (bean instanceof Map && entityName == null)
			throw new RuntimeException("Cannot create a PersistentBean from a Map without an entity name");
		if ( !(bean instanceof Map) && entityName == null)
			entityName = ClassNameUtil.getEntityNameForBean(bean);
		EntityPersister ep = ((SessionFactoryImpl)session.getSessionFactory()).getEntityPersister(entityName);
		return HibPersistentBeanFactory.getInstance(session).
				createPersistentBeanFromObject(bean, ep);
	}
	
	/**
	 * Create a PersistentBean from an entity (either Pojo or Map).
	 * This only works with Hibernate-created entities, not brand-new Pojo or Map objects
	 * that have not yet been saved.
	 * @param entity The entity in question, can be either a Pojo or a Map
	 * @return A new PersistentBean
	 */
	public PersistentBean createPersistentBeanFromEntity(Object entity) {
		String entityName = session.getEntityName(entity);
		return createPersistentBeanFromEntity(entity, entityName);
	}
	
	/**
	 * Make a shallow copy of the given Pojo or Map object. 
	 * The resulting PersistentBean will contain a copy
	 * of all the attributes and single-valued relationships. Access to the collections will
	 * be deferred to the original bean passed here.
	 */
	public PersistentBean createPersistentBeanCopyFromObject(Object bean, EntityPersister ep) {
		if (bean == null)
			return null;
		try {
		Serializable pk = ep.getIdentifier(bean, (SessionImplementor)session);
		return new HibPersistentBeanCopy(bean, pk, ep);
		}
		catch(Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Make a shallow copy of the given Pojo or Map bean. The resulting PersistentBean will contain 
	 * a copy of all the attributes and single-valued relationships. Access to the collections will
	 * be deferred to the original Pojo or Map bean passed herein.
	 */
	public static PersistentBean createPersistentBeanCopyFromState(Object[] state, Object entity, 
			EntityPersister ep, Session session) {
		
		Serializable pk = ep.getIdentifier(entity, (SessionImplementor)session);
		return new HibPersistentBeanCopy(state, entity, pk, ep, session);
	}
	
	/**
	 * Make a shallow copy of the given PersistentBean. Note that if the bean passed in is
	 * already a copy, it will be returned as is.
	 */
	public static PersistentBean copyPersistentBean(PersistentBean pbean) {
		
		// If it's already a copy, there is really no point in copying it again.
		if (pbean instanceof HibPersistentBeanCopy)
			return pbean;
		
		HibMetaEntity metaEntity = (HibMetaEntity)pbean.getMetaEntity();
		return createPersistentBeanCopyFromEntity(pbean, metaEntity.getEntityPersister());
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
 