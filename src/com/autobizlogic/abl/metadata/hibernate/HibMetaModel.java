package com.autobizlogic.abl.metadata.hibernate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.EntityMode;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.impl.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;

import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaEntity.EntityType;
import com.autobizlogic.abl.metadata.MetaModel;

/**
 * Implementation of MetaModel for Hibernate.
 */
public class HibMetaModel implements MetaModel {
	
	private SessionFactory sessionFactory;
	private EntityType entityType;
	
	private Map<String, MetaEntity> metaEntities = new HashMap<String, MetaEntity>();
	
	private boolean allMetaEntitiesRetrieved = false;

	public HibMetaModel(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	public EntityType getEntityType() {
		
		if (entityType != null)
			return entityType;
		
		EntityMode entityMode = ((SessionFactoryImpl)sessionFactory).getSettings().getDefaultEntityMode();
		if (entityMode.equals(EntityMode.POJO))
			entityType = EntityType.POJO;
		else if (entityMode.equals(EntityMode.MAP))
			entityType = EntityType.MAP;
		else
			throw new RuntimeException("Hibernate session factory has a default entity mode of " + entityMode +
					", which is neither POJO nor MAP. This is not supported.");
		
		return entityType;
	}
	
	/**
	 * Get the meta entity with the given name. If there is no such meta entity, return null;
	 */
	@Override
	public MetaEntity getMetaEntity(String name) {
		
		MetaEntity metaEntity = metaEntities.get(name);
		if (metaEntity != null)
			return metaEntity;
		
		if (allMetaEntitiesRetrieved)
			return null;
		
		EntityPersister persister = null;
		try {
			persister = ((SessionFactoryImpl)sessionFactory).getEntityPersister(name);
		}
		catch(MappingException mex) {
			return null;
		}
		
		synchronized(metaEntities) {
			if (metaEntities.get(name) == null) {
				metaEntity = new HibMetaEntity(persister, getEntityType(), this);
				metaEntities.put(name, metaEntity);
			}
			else {
				metaEntity = metaEntities.get(name);
			}
		}
		
		return metaEntity;
	}
	
	@Override
	public Collection<MetaEntity> getAllMetaEntities() {
		if ( ! allMetaEntitiesRetrieved) {
			for (String entityName : sessionFactory.getAllClassMetadata().keySet()) {
				getMetaEntity(entityName);
			}
		}
		allMetaEntitiesRetrieved = true;
		return metaEntities.values();
	}
	
	//////////////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	// We do not override equals because we want identity equals.
	
	@Override
	public String toString() {
		return "HibMetaModel for session factory " + sessionFactory;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibMetaModel.java 678 2012-02-04 00:14:03Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 