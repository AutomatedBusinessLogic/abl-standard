package com.autobizlogic.abl.data.hibernate;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.hibernate.Session;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.metadata.MetaProperty;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.BeanMap;
import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.ObjectUtil;

/**
 * An abstraction for a persistent entity, regardless of its type (POJO, Map, etc...)
 */
public class HibPersistentBean implements PersistentBean {

	protected Object bean;
	
	protected BeanMap beanMap;
	
	protected Map<String, Object> map;
	
	protected Serializable pk;
	
	protected MetaEntity metaEntity;
	
	protected Session session;
	
	/**
	 * Create from a persistent bean, either Pojo or Map.
	 * @param bean A Hibernate persistent bean (can be a proxy)
	 * @param pk The primary key for the given bean
	 * @param persister The persister for the given bean.
	 */
	@SuppressWarnings("unchecked")
	protected HibPersistentBean(Object bean, Serializable pk, EntityPersister persister, Session session) {
		
		if (bean instanceof Map) {
			try {
				this.map = (Map<String, Object>)bean;
			}
			catch(Exception ex) {
				throw new RuntimeException("Map is not Map<String, Object> for type " + persister.getEntityName());
			}
		}
		else {
			this.bean = getRawBean(bean);
			beanMap = new BeanMap(this.bean);
			map = beanMap;
		}
		this.pk = pk;
		
		this.metaEntity = MetaModelFactory.getHibernateMetaModel(persister.getFactory())
				.getMetaEntity(persister.getEntityName());
		this.session = session;
	}
	
	/**
	 * Get the Hibernate session for this bean.
	 */
	public Session getSession() {
		return session;
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
		if (bean != null)
			return getMetaEntity().getEntityClass();
		
		throw new RuntimeException("PersistentBean is not a POJO -- asking for its class makes no sense.");
	}
	
	/**
	 * Whether this is a POJO.
	 */
	@Override
	public boolean isPojo() {
		return bean != null;
	}
	
	/**
	 * Whether this is a Map.
	 */
	@Override
	public boolean isMap() {
		return map != null;
	}
	
	/**
	 * Get the underlying bean. This only works if this is a POJO, and will throw an exception
	 * if not.
	 */
	@Override
	public Object getBean() {
		if (bean == null)
			throw new RuntimeException("This PersistentBean (instance of " + 
					metaEntity.getEntityName() + ", pk " + pk + 
					") is not a POJO -- you cannot ask for its bean");
		return bean;
	}
	
	/**
	 * Get the underlying map. This only works if this is a Map, and will throw an exception if not.
	 */
	@Override
	public Map<String, Object> getMap() {
		if (map == null)
			throw new RuntimeException("This PersistentBean is not a Map -- you cannot ask for its map");
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
	 * Get the name of the primary key property.
	 */
	public String getIdentifierName() {
		return getMetaEntity().getIdentifierName();
	}
	
	/**
	 * Get the type of the given attribute. If the attribute is a collection, the type returned is that
	 * of the members of the collection.
	 */
	public String getClassTypeForAttribute(String attribName) {
		MetaProperty metaProp = metaEntity.getMetaProperty(attribName);
		if (metaProp instanceof MetaAttribute) {
			MetaAttribute metaAtt = (MetaAttribute)metaProp;
			return metaAtt.getType().getName();
		}
		
		MetaRole metaRole = (MetaRole)metaProp;
		return metaRole.getOtherMetaEntity().getEntityName();
	}
	
	/**
	 * Make a shallow copy of this bean.
	 */
	@Override
	public PersistentBean duplicate() {
		return HibPersistentBeanFactory.copyPersistentBean(this);
	}
	
	//////////////////////////////////////////////////////////////////////
	// Implementation of Map
	
	@Override
	public void clear() {
		if (beanMap != null)
			throw new RuntimeException("clear not implemented for a POJO bean");
		
		map.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		if (beanMap != null)
			return beanMap.containsKey(key);
		
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		if (beanMap != null)
			throw new RuntimeException("containsValue not implemented for a POJO bean");
		
		return map.containsValue(value);
	}

	@Override
	public Set<Map.Entry<String, Object>> entrySet() {
		if (beanMap != null)
			throw new RuntimeException("entrySet not implemented for a POJO bean");
		
		return map.entrySet();
	}

	@Override
	public Object get(Object key) {
		if (beanMap != null)
			return beanMap.get(key);
		
		return map.get(key);
	}

	@Override
	public boolean isEmpty() {
		if (beanMap != null)
			return false;
		
		return map.isEmpty();
	}

	@Override
	public Set<String> keySet() {
		if (beanMap != null)
			return beanMap.keySet();
		
		return map.keySet();
	}

	@Override
	public Object put(String key, Object value) {
		if (beanMap != null)
			return beanMap.put(key, value);
		
		return map.put(key, value);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> theMap) {
		if (beanMap != null)
			beanMap.putAll(theMap);
		
		this.map.putAll(theMap);
	}

	@Override
	public Object remove(Object key) {
		if (beanMap != null)
			throw new RuntimeException("remove not implemented for a POJO bean");
		
		return map.remove(key);
	}

	@Override
	public int size() {
		if (beanMap != null)
			throw new RuntimeException("size not implemented for a POJO bean");
		
		return map.size();
	}

	@Override
	public Collection<Object> values() {
		if (beanMap != null)
			throw new RuntimeException("values not implemented for a POJO bean");
		
		return map.values();
	}
	
	@Override
	public String toShortString() {
		String entName = NodalPathUtil.getNodalPathLastName(metaEntity.getEntityName());
		if (pk == null)
			return entName + "[primary key not yet assigned]";
		
		return entName + "[" + pk.toString() + "]";
	}

	///////////////////////////////////////////////////////////////////////////
	// Internal methods
	
	/**
	 * If the object is a HibernateProxy, get the underlying object.
	 */
	private static Object getRawBean(Object o) {
		if (o instanceof HibernateProxy) {
			HibernateProxy hp = (HibernateProxy)o;			
			o = hp.getHibernateLazyInitializer().getImplementation();
		}
		
		return o;
	}
	
	///////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(NodalPathUtil.getNodalPathLastName(metaEntity.getEntityName()));
		sb.append("[");
		sb.append(ObjectUtil.safeToString(getPk()));
		sb.append("] = [");
		Set<MetaAttribute> metaAtts = metaEntity.getMetaAttributes();
		List<MetaAttribute> sortedMetaAtts = new Vector<MetaAttribute>();
		sortedMetaAtts.addAll(metaAtts);
		Collections.sort(sortedMetaAtts, new Comparator<MetaAttribute>(){
			@Override
			public int compare(MetaAttribute ma1, MetaAttribute ma2) {
				if (ma1 == null || ma2 == null)
					throw new RuntimeException("Cannot compare null meta attributes");
				if (ma1.getName() == null || ma2.getName() == null)
					throw new RuntimeException("Cannot compare meta attributes with null names");
				return ma1.getName().compareTo(ma2.getName());
			}
		});
		for (MetaAttribute metaAtt : sortedMetaAtts) {
			sb.append(metaAtt.getName());
			sb.append("=");
			sb.append(ObjectUtil.safeToString(get(metaAtt.getName())));
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
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibPersistentBean.java 1256 2012-04-24 07:19:42Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 