package com.autobizlogic.abl.metadata;

import java.util.Set;

/**
 * Metadata for a specific persistent bean, whether it's a POJO, a Map or whatever.
 */
public interface MetaEntity {
	
	public enum EntityType {
		POJO,
		MAP
	}
	
	/**
	 * Get the metamodel to which this metaentity belongs
	 */
	public MetaModel getMetaModel();

	/**
	 * Get the name of this entity.
	 */
	public String getEntityName();
	
	/**
	 * Get the class of the persistent bean used to represent this entity. This obviously
	 * only makes sense if the entity is a POJO. In any other case, an exception is thrown.
	 * @return
	 */
	public Class<?> getEntityClass();
	
	/**
	 * Get the name of the attribute that's the identifier (primary key) for this entity.
	 */
	public String getIdentifierName();
	
	/**
	 * Get the type of the entity, i.e. whether it's a POJO, a Map, or whatever.
	 */
	public EntityType getEntityType();
	
	/**
	 * Whether this entity is a POJO.
	 */
	public boolean isPojo();

	/**
	 * Whether this entity is a Map.
	 */
	public boolean isMap();
	
	/**
	 * Get the metadata for the specified attribute. If the attribute does not exist,
	 * return null.
	 */
	public MetaAttribute getMetaAttribute(String attributeName);
	
	/**
	 * Get the metadata for all attributes in this entity.
	 */
	public Set<MetaAttribute> getMetaAttributes();
	
	/**
	 * Get the role with the given name. If no such relationship exists, return null.
	 * @param roleName The name of the role from this entity.
	 */
	public MetaRole getMetaRole(String roleName);
	
	/**
	 * Get either a MetaAttribute or a MetaRelationship based on its name.
	 */
	public MetaProperty getMetaProperty(String propertyName);
	
	/**
	 * Get all metaproperties for this entity.
	 */
	public Set<MetaProperty> getMetaProperties();
	
	/**
	 * Get all the roles from this entity to its parent entities. If there are none,
	 * an empty set is returned.
	 */
	public Set<MetaRole> getRolesFromChildToParents();
	
	/**
	 * Get all the roles from this entity to its child entities. If there are none,
	 * an empty set is returned.
	 * @return
	 */
	public Set<MetaRole> getRolesFromParentToChildren();	
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 