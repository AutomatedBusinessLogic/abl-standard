package com.autobizlogic.abl.data.hibernate;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.hibernate.Session;
import org.hibernate.engine.EntityKey;
import org.hibernate.engine.PersistenceContext;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.hibernate.HibernateSessionUtil;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.metadata.MetaProperty;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.BeanMap;

/**
 * An abstraction for a persistent entity, in which all the non-collection
 * properties are copied, so that if the underlying entity changes later on, this
 * will not be affected.
 */
public class HibPersistentBeanCopy implements PersistentBean {

	/**
	 * We keep the bean around so that requests for collections can be forwarded to it.
	 */
	private Object bean;
	
	/**
	 * We use that to retrieve values from the bean if we have to.
	 */
	@SuppressWarnings("rawtypes")
	private Map beanMap;
	
	/**
	 * We keep the map around so that requests for collections can be forwarded to it.
	 */
	private Map<String, Object> map;

	/**
	 * The primary key for this object.
	 */
	private Serializable pk;
	
	/**
	 * The metadata for this object.
	 */
	private MetaEntity metaEntity;

	/*
	 * The copied values.
	 */
	private Map<String, Object> values = new HashMap<String, Object>();


	/**
	 * Create from a persistent bean.
	 * @param bean A Hibernate persistent bean (can be a proxy) - either Pojo or Map, or even
	 * a PersistentBean.
	 * @param pk The primary key for the given bean
	 * @param persister The persister for the given bean.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected HibPersistentBeanCopy(Object bean, Serializable pk, EntityPersister persister) {
		this.pk = pk;
		this.metaEntity = MetaModelFactory.getHibernateMetaModel(persister.getFactory())
				.getMetaEntity(persister.getEntityName());
		if (metaEntity == null)
			throw new RuntimeException("Could not find metadata from entity " + persister.getEntityName());
		
		if (bean instanceof HibPersistentBean) {
			HibPersistentBean pb = (HibPersistentBean)bean;
			this.bean = pb.bean;
			this.map = pb.map;
			this.beanMap = pb.beanMap;
		}
		else if (bean instanceof HibPersistentBeanCopy) {
			throw new RuntimeException("It makes no sense to make a copy of a HibPersistentBeanCopy");
		}
		else if (bean instanceof Map) {
			beanMap = (Map)bean;
			this.map = (Map<String, Object>)bean;
		}
		else {
			beanMap = new BeanMap(bean);
			this.bean = bean;
			map = beanMap;
		}
		
		if (map == null)
			throw new RuntimeException("No map defined for HibPersistentBeanCopy");
		
		// Copy all attributes
		for (MetaAttribute metaAttrib : metaEntity.getMetaAttributes()) {
			Object value = map.get(metaAttrib.getName());
			values.put(metaAttrib.getName(), value);
		}

		// Copy single-valued relationships
		for (MetaRole metaRole : metaEntity.getRolesFromChildToParents()) {
			String roleName = metaRole.getRoleName();
			Object value = map.get(roleName);
			values.put(roleName, value);
		}
	}
		
	/**
	 * Create from a state array.
	 * @param The state (typically from a Hibernate event)
	 * @param pk The primary key
	 * @param persister The persister for the object
	 */
	@SuppressWarnings("unchecked")
	protected HibPersistentBeanCopy(Object[] state, Object entity, Serializable pk, EntityPersister persister, Session session) {
		if (entity instanceof Map)
			map = (Map<String, Object>)entity;
		else {
			bean = entity;
			beanMap = new BeanMap(entity);
		}
		this.pk = pk;
		this.metaEntity = MetaModelFactory.getHibernateMetaModel(persister.getFactory()).
				getMetaEntity(persister.getEntityName());
		
		ClassMetadata metadata = persister.getClassMetadata();
		String[] propNames = metadata.getPropertyNames();
		for (int i = 0; i < propNames.length; i++) {
			String propName = propNames[i];
			if (metaEntity.getMetaProperty(propName).isCollection())
				continue;
			
			MetaRole metaRole = metaEntity.getMetaRole(propName);
			if (metaRole == null) { // Not a relationship -- must be an attribute
				if (state[i] != null)
					values.put(propName, state[i]);
			}
			else if ( ! metaRole.isCollection()) {
				// In the case of old values, when we are handed the state, it contains the pk for associations,
				// and not (as you'd expect) the object itself. So we check whether the value is a real object,
				// and if it's not, we grab it from the object.
				if (state[i] == null || session.contains(state[i])) {
					values.put(propName, state[i]);
				}
				else {
					// We have a pk instead of a proxy -- ask Hibernate to create a proxy for it.
					String className = metadata.getPropertyType(propName).getReturnedClass().getName();
					PersistenceContext persContext = HibernateSessionUtil.getPersistenceContextForSession(session);
					EntityPersister entityPersister = HibernateSessionUtil.getEntityPersister(session, className, bean);
					EntityKey entityKey = new EntityKey((Serializable)state[i], entityPersister, session.getEntityMode());
					
					// Has a proxy already been created for this session?
					Object proxy = persContext.getProxy(entityKey);
					if (proxy == null) {
						// There is no proxy anywhere for this object, so ask Hibernate to create one for us
						proxy = entityPersister.createProxy((Serializable)state[i], (SessionImplementor)session);
						persContext.getBatchFetchQueue().addBatchLoadableEntityKey(entityKey);
						persContext.addProxy(entityKey, proxy);
					}
					values.put(propName, proxy);
				}				
			}
		}
	}

	/**
	 * Get the meta entity for this entity.
	 */
	@Override
	public MetaEntity getMetaEntity() {
		return metaEntity;
	}
	
	/**
	 * Get the primary key for this entity.
	 */
	@Override
	public Serializable getPk() {
		if (pk == null) {
			pk = (Serializable)this.get(getIdentifierName());
		}
		
		return pk;
	}
	
	/**
	 * Get the name of this entity. If the entity is a POJO, this will usually be the
	 * full class name, e.g. com.foo.Customer. If the entity is a map, this will usually
	 * be a simple name (e.g. Customer).
	 */
	@Override
	public String getEntityName() {
		return getMetaEntity().getEntityName();
	}
	
	/**
	 * If this entity is a POJO, get the class for the persistent bean, e.g. com.foo.Customer.
	 */
	public Class<?> getMappedClass() {
		if (metaEntity.isPojo())
			return getMetaEntity().getEntityClass();
		
		throw new RuntimeException("PersistentBean is not a POJO -- asking for its class makes no sense.");
	}
	
	/**
	 * Whether this is a POJO.
	 */
	@Override
	public boolean isPojo() {
		return metaEntity.isPojo();
	}
	
	/**
	 * Whether this is a Map.
	 */
	@Override
	public boolean isMap() {
		return metaEntity.isMap();
	}
	
	/**
	 * Get the underlying bean. This only works if this is a POJO, and will throw an exception
	 * if not.
	 */
	@Override
	public Object getBean() {
		if (bean == null)
			throw new RuntimeException("Attempted to access bean of HibPersistentBeanCopy without one - " +
					metaEntity.getEntityName() + ", pk " + pk);
		return bean;
	}
	
	/**
	 * Get the underlying map. This only works if this is a Map, and will throw an exception if not.
	 */
	@Override
	public Map<String, Object> getMap() {
		if (map == null)
			throw new RuntimeException("Attempted to access map of HibPersistentBeanCopy without one - " +
					metaEntity.getEntityName() + ", pk " + pk);
		return map;
	}
	
	/**
	 * Get the underlying Hibernate entity, which can be either a Pojo or a Map.
	 */
	@Override
	public Object getEntity() {
		if (isPojo())
			return getBean();
		
		return getMap();
	}
	
	/**
	 * It makes no sense to copy a copy, so this method returns this.
	 */
	@Override
	public PersistentBean duplicate() {
		return this;
	}
	
	/**
	 * Get the name of the primary key property.
	 */
	public String getIdentifierName() {
		return getMetaEntity().getIdentifierName();
	}
	
	/**
	 * Get the type of the given property. If the property is a collection, the type returned is that
	 * of the members of the collection.
	 */
	public String getClassTypeForProperty(String propName) {
		MetaProperty metaProp = metaEntity.getMetaProperty(propName);
		if (metaProp instanceof MetaAttribute) {
			MetaAttribute metaAtt = (MetaAttribute)metaProp;
			return metaAtt.getType().getName();
		}
		
		MetaRole metaRole = (MetaRole)metaProp;
		return metaRole.getOtherMetaEntity().getEntityName();
	}
	
	//////////////////////////////////////////////////////////////////////
	// Implementation of Map
	
	@Override
	public void clear() {
		throw new RuntimeException("You cannot call clear() on a HibPersistentBeanCopy");
	}

	@Override
	public boolean containsKey(Object key) {
		return values.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return values.containsValue(value);
	}

	@Override
	public Set<Map.Entry<String, Object>> entrySet() {
		return values.entrySet();
	}

	/**
	 * If the desired property is a collection, we forward that to the original
	 * object (bean or map), otherwise retrieve from our copy.
	 */
	@Override
	public Object get(Object key) {
		String name = (String)key;
		MetaProperty metaProperty = metaEntity.getMetaProperty(name);
		if (metaProperty == null)
			return beanMap.get(name);
		
		// Defer collections to the original object
		if (metaProperty.isCollection()) {
			if (metaEntity.isPojo())
				return beanMap.get(name);
			if (metaEntity.isMap())
				return map.get(name);
			throw new RuntimeException("This should never happen: HibPersistentBean is neither POJO nor Map?");
		}
		
		// Otherwise return the value we hold
		return values.get(key);
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	@Override
	public Set<String> keySet() {
		return values.keySet();
	}

	@Override
	public Object put(String key, Object value) {
		throw new RuntimeException("You cannot call put() on a HibPersistentBeanCopy");
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> theMap) {
		throw new RuntimeException("You cannot call putAll() on a HibPersistentBeanCopy");
	}

	@Override
	public Object remove(Object key) {
		throw new RuntimeException("You cannot call remove() on a HibPersistentBeanCopy");
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public Collection<Object> values() {
		return values.values();
	}

	///////////////////////////////////////////////////////////////////////////
	// Internal methods
	
	/**
	 * Store the given value under the given name, no questions asked.
	 */
	protected void setValue(String name, Object value) {
		values.put(name, value);
	}
	
	///////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(metaEntity.getEntityName());
		sb.append("[");
		sb.append(pk.toString());
		sb.append("][");
		Set<MetaAttribute> metaAtts = metaEntity.getMetaAttributes();
		List<MetaAttribute> sortedMetaAtts = new Vector<MetaAttribute>();
		sortedMetaAtts.addAll(metaAtts);
		Collections.sort(sortedMetaAtts, new Comparator<MetaAttribute>(){
			@Override
			public int compare(MetaAttribute ma1, MetaAttribute ma2) {
				return ma1.getName().compareTo(ma2.getName());
			}
		});
		for (MetaAttribute metaAtt : sortedMetaAtts) {
			sb.append(metaAtt.getName());
			sb.append("=");
			sb.append(this.get(metaAtt.getName()));
			sb.append(", ");
		}
		Set<MetaRole> rolesToParents = metaEntity.getRolesFromChildToParents();
		for (MetaRole roleToParent : rolesToParents) {
			sb.append(roleToParent.getRoleName());
			sb.append("=");
			sb.append("<");
			sb.append(roleToParent.getOtherMetaEntity().getEntityName());
			if (get(roleToParent.getRoleName()) == null)
				sb.append(" (null)");
			sb.append(">,");
		}
		Set<MetaRole> rolesToChildren = metaEntity.getRolesFromParentToChildren();
		for (MetaRole roleToChild : rolesToChildren) {
			sb.append(roleToChild.getRoleName());
			sb.append("=");
			sb.append("<collection of ");
			sb.append(roleToChild.getOtherMetaEntity().getEntityName());
			sb.append(">,");
		}
		sb.append("]");
		return sb.toString();
	}

	@Override
	public String toShortString() {
		return metaEntity.getEntityName() + "[" + pk.toString() + "]";
	}

	/**
	 * Evaluates to true if the two beans are for the same entity and have the same primary key.
	 */
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o == this)
			return true;
		if ( ! (o instanceof PersistentBean))
			return false;
		PersistentBean theBean = (PersistentBean)o;
		if ( ! getMetaEntity().equals(theBean.getMetaEntity()))
			return false;
		
		if ( ! getPk().equals(theBean.getPk()))
			return false;

		return true;
	}
	
	@Override
	public int hashCode() {
		return getMetaEntity().hashCode() + getPk().hashCode();
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibPersistentBeanCopy.java 1257 2012-04-24 08:57:06Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 