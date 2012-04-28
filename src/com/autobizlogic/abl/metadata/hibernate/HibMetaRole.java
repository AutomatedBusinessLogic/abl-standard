package com.autobizlogic.abl.metadata.hibernate;

import org.hibernate.EntityMode;
import org.hibernate.SessionFactory;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.collection.AbstractCollectionPersister;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.CollectionType;
import org.hibernate.type.EntityType;
import org.hibernate.type.ManyToOneType;
import org.hibernate.type.OneToOneType;
import org.hibernate.type.Type;

import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * Represents one side of a relationship between two entities.
 */
public class HibMetaRole implements MetaRole {

	private HibMetaEntity metaEntity;
	private HibMetaEntity otherMetaEntity;
	private String roleName;
	private boolean isCollection;
	private MetaRole otherRole;

	/**
	 * Remember whether we've already tried to find the other role. If there isn't one,
	 * then it will be null.
	 */
	private boolean otherRoleDetermined;
	
	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.PERSISTENCE);

	protected HibMetaRole(HibMetaEntity metaEntity, String roleName, boolean isCollection){
		this.metaEntity = metaEntity;
		this.roleName = roleName;
		this.isCollection = isCollection;
	}

	@Override
	public boolean isAttribute() {
		return false;
	}

	@Override
	public boolean isRelationship() {
		return true;
	}

	/**
	 * Get the role name. Equivalent to getRoleName.
	 */
	@Override
	public String getName() {
		return getRoleName();
	}

	@Override
	public String getRoleName() {
		return roleName;
	}

	@Override
	public MetaEntity getMetaEntity() {
		return metaEntity;
	}

	@Override
	public MetaRole getOtherMetaRole() {

		retrieveOtherMetaRole();
		return otherRole;		
	}

	@Override
	public boolean isCollection() {
		return isCollection;
	}

	/**
	 * Get the MetaEntity at the other end of this role.
	 */
	@Override
	public HibMetaEntity getOtherMetaEntity() {

		if (otherMetaEntity != null)
			return otherMetaEntity;

		SessionFactoryImplementor sfi = (SessionFactoryImplementor)metaEntity.getMetaModel().getSessionFactory();		
		EntityPersister thisPers = metaEntity.getPersister();
		Type type = thisPers.getPropertyType(roleName);
		if (type.isCollectionType() && !isCollection)
			throw new RuntimeException("Internal metadata inconsistency: role name " + roleName +
					" of " + metaEntity.getEntityName() + " is and isn't a collection");
		else if (type.isEntityType() && isCollection)
			throw new RuntimeException("Internal metadata inconsistency: role name " + roleName +
					" of " + metaEntity.getEntityName() + " is and isn't an entity");

		String otherEntityName = null;
		if (isCollection) {
			CollectionType ctype = (CollectionType)type;
			otherEntityName = ctype.getAssociatedEntityName(sfi);
		}
		else {
			EntityType etype = (EntityType)type;
			otherEntityName = etype.getAssociatedEntityName(sfi);
		}

		otherMetaEntity = (HibMetaEntity)metaEntity.getMetaModel().getMetaEntity(otherEntityName);
		if (otherMetaEntity == null)
			throw new RuntimeException("Unable to find entity " + otherEntityName + 
					", which is the value of role " + metaEntity.getEntityName() + "." + roleName);

		return otherMetaEntity;
	}
	

	///////////////////////////////////////////////////////////////////////////////

	/**
	 * Internal method: retrieve the inverse role.
	 */
	private void retrieveOtherMetaRole() {

		if (otherRoleDetermined)
			return;
		
		String[] inverse = getInverseOfRole(this.getMetaEntity().getEntityName(), this.getRoleName());
		if (inverse != null) {
			otherMetaEntity = (HibMetaEntity)getMetaEntity().getMetaModel().getMetaEntity(inverse[0]);
			otherRole = otherMetaEntity.getMetaRole(inverse[1]);
		}

		otherRoleDetermined = true;
	}

	/**
	 * Get the opposite role of the given role, if it exists
	 * @param entityName The name of the entity who owns the role, e.g. com.foo.Customer
	 * @param rName The role name, e.g. orders
	 * @return Null if the role has no known inverse, or [entity name, role name] of the inverse
	 */
	private String[] getInverseOfRole(String entityName, String rName) {
		
		SessionFactory sessFact = ((HibMetaModel)getMetaEntity().getMetaModel()).getSessionFactory();
		ClassMetadata classMeta = sessFact.getClassMetadata(entityName);
		Type propType = classMeta.getPropertyType(rName);
		if (propType.isCollectionType()) {
			return getInverseOfCollectionRole(entityName, rName);
		}
		
		if (propType.isEntityType()) {
			return getInverseOfSingleRole(entityName, rName);
		}
		
		log.debug("Role " + entityName + "." + rName + " is neither a collection type " +
				"nor an entity type, and will be assumed to have no inverse.");
		return null;
	}
	
	/**
	 * Get the inverse of a one-to-many role
	 * @param entityName The entity owning the role
	 * @param rName The role name
	 * @return Null if no inverse was found, otherwise [entity name, role name] of the inverse role
	 */
	private String[] getInverseOfCollectionRole(String entityName, String rName) {
		String[] result = new String[2];
		
		SessionFactory sessFact = ((HibMetaModel)getMetaEntity().getMetaModel()).getSessionFactory();
		AbstractCollectionPersister parentMeta = (AbstractCollectionPersister)sessFact.getCollectionMetadata(entityName + "." + rName);
		if (parentMeta == null) { // Could be inherited -- search through superclasses
			while (parentMeta == null) {
				Class<?> cls = null;
				if (getMetaEntity().getEntityType() == MetaEntity.EntityType.POJO)
					cls = sessFact.getClassMetadata(entityName).getMappedClass(EntityMode.POJO);
				else
					cls = sessFact.getClassMetadata(entityName).getMappedClass(EntityMode.MAP);
				Class<?> superCls = cls.getSuperclass();
				if (superCls.getName().equals("java.lang.Object"))
					throw new RuntimeException("Unable to retrieve Hibernate information for collection " + entityName + "." + rName);
				ClassMetadata clsMeta = sessFact.getClassMetadata(superCls);
				if (clsMeta == null)
					throw new RuntimeException("Unable to retrieve Hibernate information for collection " + 
							entityName + "." + rName + ", even from superclass(es)");
				entityName = clsMeta.getEntityName();
				parentMeta = (AbstractCollectionPersister)sessFact.getCollectionMetadata(entityName + "." + rName);
			}
		}
		String[] colNames = parentMeta.getKeyColumnNames();
		String childName = parentMeta.getElementType().getName();

		AbstractEntityPersister childMeta = (AbstractEntityPersister)sessFact.getClassMetadata(childName);
		String[] propNames = childMeta.getPropertyNames();
		for (int i = 0; i < propNames.length; i++) {
			Type type = childMeta.getPropertyType(propNames[i]);
			if ( !type.isEntityType())
				continue;
			EntityType entType = (EntityType)type;
			if ( ! entType.getAssociatedEntityName().equals(entityName))
				continue;
			String[] cnames = childMeta.getPropertyColumnNames(i);
			if (cnames.length != colNames.length)
				continue;
			boolean columnMatch = true;
			for (int j = 0; j < cnames.length; j++) {
				if ( ! cnames[j].equals(colNames[j])) {
					columnMatch = false;
					break;
				}
			}
			if (columnMatch) {
				result[0] = childName;
				result[1] = propNames[i];
				return result;
			}
		}

		return null;
	}

	/**
	 * Get the inverse of a many-to-one role
	 * @param entityName The entity owning the role
	 * @param rName The role name
	 * @return Null if no inverse was found, otherwise [entity name, role name] of the inverse role
	 */
	private String[] getInverseOfSingleRole(String entityName, String rName) {
		String[] result = new String[2];

		SessionFactory sessFact = ((HibMetaModel)getMetaEntity().getMetaModel()).getSessionFactory();
		AbstractEntityPersister childMeta = (AbstractEntityPersister)sessFact.getClassMetadata(entityName);
		int propIdx = childMeta.getPropertyIndex(rName);
		String[] cnames = childMeta.getPropertyColumnNames(propIdx);
		Type parentType = childMeta.getPropertyType(rName);
		if (parentType instanceof OneToOneType)
			return getInverseOfOneToOneRole(entityName, rName);
		if ( ! (parentType instanceof ManyToOneType))
			throw new RuntimeException("Inverse of single-valued role " + entityName + "." + rName + 
					" is neither single-valued not multi-valued");
		ManyToOneType manyType = (ManyToOneType)parentType;
		String parentEntityName = manyType.getAssociatedEntityName();
		
		AbstractEntityPersister parentMeta = (AbstractEntityPersister)sessFact.getClassMetadata(parentEntityName);
		String[] propNames = parentMeta.getPropertyNames();
		for (int i = 0; i < propNames.length; i++) {
			Type type = parentMeta.getPropertyType(propNames[i]);
			if ( ! type.isCollectionType())
				continue;
			CollectionType collType = (CollectionType)type;
			if ( ! collType.getAssociatedEntityName((SessionFactoryImplementor)sessFact).equals(entityName))
				continue;

			AbstractCollectionPersister persister = (AbstractCollectionPersister)
					sessFact.getCollectionMetadata(parentEntityName + "." + propNames[i]);
			String[] colNames = persister.getKeyColumnNames();
			if (cnames.length != colNames.length)
				continue;
			boolean columnMatch = true;
			for (int j = 0; j < cnames.length; j++) {
				if ( ! cnames[j].equals(colNames[j])) {
					columnMatch = false;
					break;
				}
			}
			if (columnMatch) {
				result[0] = parentEntityName;
				result[1] = propNames[i];
				return result;
			}
		}
		
		return null;
	}

	@SuppressWarnings("static-method")
	private String[] getInverseOfOneToOneRole(String entityName, String rName) {
		
		throw new RuntimeException("One to one relationships are not yet supported (coming soon, though)");
	}

	/////////////////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o == this)
			return true;
		if ( ! (o instanceof HibMetaRole))
			return false;
		HibMetaRole role = (HibMetaRole)o;
		// We no longer compare the entities because it might be inherited.
//		if ( ! role.getMetaEntity().equals(metaEntity))
//			return false;
		if ( ! role.getRoleName().equals(roleName))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		return getMetaEntity().hashCode() + roleName.hashCode();
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Meta role ");
		sb.append(metaEntity.getEntityName());
		sb.append(".");
		sb.append(roleName);
		sb.append(" (");
		if (isCollection)
			sb.append("collection of ");
		MetaRole otherMetaRole = getOtherMetaRole();
		if (otherMetaRole == null) {
			sb.append("** otherMetaRole is null (bi-directional relationships recommended)");
		} else {
			sb.append(otherMetaRole.getMetaEntity().getEntityName());
			sb.append(", inverse is ");
			sb.append(otherMetaRole.getRoleName());
		}
		sb.append(")");
		return sb.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  HibMetaRole.java 1201 2012-04-19 09:56:15Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 