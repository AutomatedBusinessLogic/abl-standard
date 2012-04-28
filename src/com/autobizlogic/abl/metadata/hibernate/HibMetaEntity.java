package com.autobizlogic.abl.metadata.hibernate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.EntityMode;
import org.hibernate.QueryException;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.ComponentType;
import org.hibernate.type.Type;

import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaProperty;
import com.autobizlogic.abl.metadata.MetaRole;

/**
 * The implementation of MetaEntity for Hibernate.
 */
public class HibMetaEntity implements MetaEntity {
	
	public static final String DEFAULT_PK_NAME = "__PK";

	private EntityPersister persister;
	private HibMetaModel metaModel;
	
	private EntityType entityType;
	
	/**
	 * Cache all meta attributes here
	 */
	private Map<String, MetaAttribute> metaAttributes;

	/**
	 * Cache all meta roles here
	 */
	private Map<String, MetaRole> metaRoles;

	/**
	 * This gets set to true if we know that all meta properties have been figured out.
	 * It allows us to take shortcuts.
	 */
	private boolean allPropsRetrieved = false;
	
	/**
	 * This is protected because it should only be called by PersistentEntityFactory.
	 */
	protected HibMetaEntity(EntityPersister persister, EntityType entityType, HibMetaModel metaModel) {
		this.persister = persister;
		this.entityType = entityType;
		this.metaModel = metaModel;
	}
	
	@Override
	public HibMetaModel getMetaModel() {
		return metaModel;
	}
	
	@Override
	public String getEntityName() {
		return persister.getEntityName();
	}
	
	@Override
	public Class<?> getEntityClass() {
		return persister.getMappedClass(EntityMode.POJO);
	}
	
	@Override
	public String getIdentifierName() {
		return persister.getIdentifierPropertyName();
	}
	
	@Override
	public EntityType getEntityType() {
		return entityType;
	}
	
	@Override
	public boolean isPojo() {
		return entityType == EntityType.POJO;
	}
	
	@Override
	public boolean isMap() {
		return entityType == EntityType.MAP;
	}
	
	@Override
	public MetaProperty getMetaProperty(String name) {
		
		loadAllProperties();
		
		MetaAttribute ma = metaAttributes.get(name);
		if ( ma != null)
			return ma;
		
		return metaRoles.get(name);
	}
	
	@Override
	public Set<MetaProperty> getMetaProperties() {
		
		loadAllProperties();
					
		Set<MetaProperty> result = new TreeSet<MetaProperty>(new Comparator<MetaProperty>(){
			@Override
			public int compare(MetaProperty mp1, MetaProperty mp2) {
				return mp1.getName().compareTo(mp2.getName());
			}});
		result.addAll(metaAttributes.values());
		result.addAll(metaRoles.values());
		return result;
	}
	
	@Override
	public MetaAttribute getMetaAttribute(String name) {
		
		loadAllProperties();
		return metaAttributes.get(name);
	}
	
	@Override
	public Set<MetaAttribute> getMetaAttributes() {
		
		loadAllProperties();
		Set<MetaAttribute> result = new TreeSet<MetaAttribute>(new Comparator<MetaAttribute>(){
			@Override
			public int compare(MetaAttribute ma1, MetaAttribute ma2) {
				return ma1.getName().compareTo(ma2.getName());
			}});
		
		result.addAll(metaAttributes.values());
		return result;
	}
	
	@Override
	public MetaRole getMetaRole(String name) {
		
		loadAllProperties();
		return metaRoles.get(name);
	}
	
	@Override
	public Set<MetaRole> getRolesFromChildToParents() {
		
		Set<MetaRole> result = new TreeSet<MetaRole>(new Comparator<MetaRole>(){
			@Override
			public int compare(MetaRole mr1, MetaRole mr2) {
				return mr1.getName().compareTo(mr2.getName());
			}});
		for (MetaProperty prop : getMetaProperties()) {
			if ( ! (prop instanceof MetaRole))
				continue;
			MetaRole role = (MetaRole)prop;
			if ( ! role.isCollection())
				result.add(role);
		}
		
		return result;
	}
	
	@Override
	public Set<MetaRole> getRolesFromParentToChildren() {

		Set<MetaRole> result = new TreeSet<MetaRole>(new Comparator<MetaRole>(){
			@Override
			public int compare(MetaRole mr1, MetaRole mr2) {
				return mr1.getName().compareTo(mr2.getName());
			}});
		for (MetaProperty prop : getMetaProperties()) {
			if ( ! (prop instanceof MetaRole))
				continue;
			MetaRole role = (MetaRole)prop;
			if (role.isCollection())
				result.add(role);
		}
		return result;
	}
	
	/**
	 * Read all the properties from Hibernate metadata
	 */
	private void loadAllProperties() {
		if (allPropsRetrieved)
			return;
		
		synchronized(this) {
			if ( ! allPropsRetrieved) {
				metaAttributes = new HashMap<String, MetaAttribute>();
				metaRoles = new HashMap<String, MetaRole>();
				ClassMetadata meta = persister.getClassMetadata();
				String[] propNames = meta.getPropertyNames();
				for (String propName : propNames) {
					Type type;
					try {
						type = persister.getClassMetadata().getPropertyType(propName);
					}
					catch(QueryException ex) {
						throw new RuntimeException("Unable to determine type for property " + 
								propName + " of entity " + persister.getEntityName());
					}
					if (type.isComponentType()) {
						// Do nothing
					}
					else if (type.isCollectionType() || type.isEntityType() || type.isAssociationType()) {
						boolean isParentToChild = type.isCollectionType();
						MetaRole mr = new HibMetaRole(this, propName, isParentToChild);
						metaRoles.put(propName, mr);
					}
					else {
						MetaAttribute ma = new HibMetaAttribute(propName, type.getReturnedClass(), false);
						metaAttributes.put(propName, ma);
					}
				}
				
				// Often the primary attribute(s) is not returned by ClassMetadata.getPropertyNames
				// So we add it by hand here
				String pkName = meta.getIdentifierPropertyName();
				
				if (pkName == null) { // Can happen for composite keys
					Type pkType = meta.getIdentifierType();
					if (pkType.isComponentType()) {
						ComponentType ctype = (ComponentType)pkType;
						String[] pnames = ctype.getPropertyNames();
						for (String pname : pnames) {
							MetaAttribute ma = new HibMetaAttribute(pname, meta.getPropertyType(pname).
									getReturnedClass(), false);
							metaAttributes.put(pname, ma);
						}
					}
					else
						throw new RuntimeException("Unexpected: anonymous PK is not composite - class " + meta.getEntityName());
				}
				else if ( ! metaAttributes.containsKey(pkName)) {
					MetaAttribute ma = new HibMetaAttribute(pkName, meta.getIdentifierType().getReturnedClass(), false);
					metaAttributes.put(pkName, ma);
				}
				
				allPropsRetrieved = true;					
			}
		}
	}
	
	/**
	 * Internal method. Get the EntityPersister for this entity.
	 */
	public EntityPersister getEntityPersister() {
		return persister;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o == this)
			return true;
		if ( ! (o instanceof HibMetaEntity))
			return false;
		HibMetaEntity meta = (HibMetaEntity)o;
		// This can only be true if the two entities are from the same metamodel.
		if ( ! meta.getMetaModel().getSessionFactory().equals(getMetaModel().getSessionFactory()))
			return false;
		if ( ! meta.getEntityName().equals(getEntityName()))
			return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		return metaModel.hashCode() + getEntityName().hashCode();
	}
	
	@Override
	public String toString() {
		return "MetaEntity (hibernate) " + persister.getEntityName();
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////
	
	protected EntityPersister getPersister() {
		return persister;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibMetaEntity.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 