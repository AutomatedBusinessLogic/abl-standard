package com.autobizlogic.abl.hibernate;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

import javassist.util.proxy.ProxyFactory;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.BeanMap;

/**
 * General-purpose utility methods for Hibernate.
 */
public class HibernateUtil {

	/**
	 * Given two persistent beans, which may or may not be proxies, determine whether they refer
	 * to the same persistent object. In other words, are they of the same class, and do they
	 * have the same primary key?
	 * If both beans are null, this will return true.
	 */
	public static boolean beansHaveSamePK(Object bean1, Object bean2, Session session) {
		
		if (bean1 == null && bean2 == null)
			return true;
		
		if (bean1 == null && bean2 != null)
			return false;
		
		if (bean1 != null && bean2 == null)
			return false;
		
		if (bean1 == bean2)
			return true;
		
		String bean1ClassName = getEntityNameForObject(bean1);
		String bean2ClassName = getEntityNameForObject(bean2);
		if ( ! bean1ClassName.equals(bean2ClassName))
			return false;
		
		SessionFactory sf = session.getSessionFactory();
		ClassMetadata meta = sf.getClassMetadata(bean1ClassName);
		if (meta == null)
			throw new RuntimeException("Unable to get Hibernate metadata for: " + bean1ClassName);
		Object pk1 = meta.getIdentifier(bean1,(SessionImplementor) session);
		Object pk2 = meta.getIdentifier(bean2,(SessionImplementor) session);
		if (pk1 == null || pk2 == null)
			return false;
		return pk1.equals(pk2);
	}
	
	/**
	 * Given an object, find its first superclass that is not a Javassist proxy class.
	 * @param bean An instance of an object, normally a persistent bean
	 * @return The name of the lowest class that is not a proxy class
	 */
	public static String getEntityNameForObject(Object bean) {
		return getEntityClassForBean(bean).getName();
	}

	/**
	 * Given a persistent bean, return the "real" class for it, namely the first
	 * superclass that is not a Javassist proxy class.
	 */
	public static Class<?> getEntityClassForBean(Object bean) {
		return getEntityClassForClass(bean.getClass());
	}
	
	/**
	 * Given a persistent class, return the "real" class for it, namely the first
	 * superclass that is not a Javassist proxy class.
	 */
	public static Class<?> getEntityClassForClass(Class<?> cls) {
		while (ProxyFactory.isProxyClass(cls)) {
			cls = cls.getSuperclass();
		}
		return cls;		
	}
	
	/**
	 * Get the value of the identifier for the given entity, whatever its type (Pojo or Map).
	 * @param entity The entity
	 * @param persister The entity's persister
	 * @return The identifier for the entity
	 */
	public static Serializable getPrimaryKeyForEntity(Object entity, EntityPersister persister) {
		
		String entityName = persister.getEntityName();
		MetaModel metaModel = MetaModelFactory.getHibernateMetaModel(persister.getFactory());
		MetaEntity metaEntity = metaModel.getMetaEntity(entityName);
		String pkName = metaEntity.getIdentifierName();
		if (entity instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>)entity;
			return (Serializable)map.get(pkName);
		}
		BeanMap beanMap = new BeanMap(entity);
		return (Serializable)beanMap.get(pkName);
	}
	
	/**
	 * Given a persistent bean, get (if available) the values originally loaded from the database
	 * when the object was read.
	 * @param bean The persistent bean (must be Pojo)
	 * @param session The current Hibernate session
	 * @return Null if the object is not known by Hibernate, otherwise an ObjectState with the original
	 * values.
	 */
	public static PersistentBean getOldStateForObject(Object bean, Session session) {
		
		if ( ! session.contains(bean))
			return null;
		
		Serializable pk = session.getIdentifier(bean);

		Class<?> beanClass = getEntityClassForBean(bean);
		String beanClassName = beanClass.getName();
		EntityPersister persister = HibernateSessionUtil.getEntityPersister(session, beanClassName, bean);
		Object[] cachedValues = persister.getDatabaseSnapshot(pk, (SessionImplementor)session);
		
		return HibPersistentBeanFactory.createPersistentBeanCopyFromState(cachedValues, 
				bean, persister, session);
	}

	/**
	 * This gets called when an inconsistent relationship is detected.
	 * An inconsistent relationship is one where the two sides of the relationship do not agree.
	 * For instance, if customer1.getPurchaseOrders().contains(po1) is true, but po1.getCustomer()
	 * is not customer1.
	 * @param backParent The parent obtained from the child
	 * @param parent The parent which contains the child
	 * @param childObject Self-explanatory
	 * @param roleToChild The role from the parent to the child
	 */
	public static void failInconsistentRelationship(Object backParent, PersistentBean parent,
			PersistentBean childObject, MetaRole roleToChild) {
		
		// A common source of problems is people forgetting to implement equals and hashCode.
		String msg = "Child object " + childObject + " points to a parent object " +
				backParent + " through role " + roleToChild.getOtherMetaRole().getRoleName() + 
				", but it is also contained by parent " + parent +
				" through role " + roleToChild.getRoleName() + 
				". When setting a relationship, it must be set at both ends, otherwise the logic " +
				"cannot execute properly.";
		if (objectLacksOwnEquals(childObject))
			msg += " A common cause for this is forgetting to implement equals() and hashCode() in your " +
					"Hibernate beans. See https://community.jboss.org/wiki/EqualsAndHashCode for details.";
		throw new RuntimeException(msg);
	}
	
	/**
	 * This gets called when an inconsistent relationship is detected.
	 * An inconsistent relationship is one where the two sides of the relationship do not agree.
	 * For instance, if customer1.getPurchaseOrders().contains(po1) is true, but po1.getCustomer()
	 * is not customer1.
	 * @param parent The parent which does not contains the child
	 * @param childObject Self-explanatory
	 * @param roleToChild The role from the parent to the child
	 */
	public static void failInconsistentRelationship(Object parent, PersistentBean childObject, MetaRole roleToChild) {
		// A common source of problems is people forgetting to implement equals and hashCode.		
		String msg = "Child object " + childObject + " points to a parent object " +
				parent + " through role " + roleToChild.getOtherMetaRole().getRoleName() + 
				", but that parent does not contain the child " +
				"through role " + roleToChild.getRoleName() + 
				". When setting a relationship, it must be set at both ends, otherwise the logic " +
				"cannot execute properly.";
		if (objectLacksOwnEquals(childObject))
			msg += " A common cause for this is forgetting to implement equals() and hashCode() in your " +
					"Hibernate beans. See https://community.jboss.org/wiki/EqualsAndHashCode for details.";
		throw new RuntimeException(msg);
	}
	
	/**
	 * Determine whether the given object lacks its own implementation of equals(). This will return true
	 * if the object does not somehow override java.lang.Object's equals.
	 * @param bean Any PersistentBean
	 */
	private static boolean objectLacksOwnEquals(PersistentBean bean) {
		if (bean == null)
			return false;
		if ( ! bean.isPojo())
			return false;
		Class<?> cls = bean.getBean().getClass();
		try {
			Method equalsMethod = cls.getMethod("equals", Object.class);
			if ("java.lang.Object".equals(equalsMethod.getDeclaringClass().getName()))
				return true;
		}
		catch(Exception ex) {
			// Ignore -- we can't deal with this right now
		}
		return false;
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
 