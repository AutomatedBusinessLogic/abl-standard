package com.autobizlogic.abl.metadata;

import java.util.Collection;

/**
 * The metadata for a set of entities.
 */
public interface MetaModel {
	
	/**
	 * Get the metadata for the given entity.
	 * @param entityName The name of the entity
	 * @return The meta entity in question, or null if no such entity exists.
	 */
	public MetaEntity getMetaEntity(String entityName);
	
	/**
	 * Get all the metaentities in this metamodel
	 */
	public Collection<MetaEntity> getAllMetaEntities();
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 