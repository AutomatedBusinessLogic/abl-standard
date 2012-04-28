package com.autobizlogic.abl.rule;

import java.util.HashMap;

import org.apache.commons.jexl2.MapContext;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.data.hibernate.HibPersistentBeanFactory;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaProperty;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.NumberUtil;

/**
 * Encapsulate a persistent bean as a context for the Jexl interpreter.
 * This also takes care of some Jexl chicanery, whereby if it asks a bean for property a, and
 * then asks property a for property b, if that returns null, then it will ask the original object
 * for property a.b (i.e. Ant-style property name).
 * <p/>
 * In the same vein, in the case of an expression like customer.balance, if customer is null,
 * Jexl will then ask for the value of balance from this same context object. We avoid this by
 * returning a NullObjectMap whenever a relationship is null.
 */
public class BeanMapContext extends MapContext {

	private PersistentBean objectState;
	private LogicContext logicContext;
	private boolean handleNullRelationships;
	
	protected BeanMapContext(PersistentBean os, LogicContext logicContext, boolean handleNullRelationships) {
		super(os);
		this.objectState = os;
		this.logicContext = logicContext;
		this.handleNullRelationships = handleNullRelationships;
	}

	@Override
	public Object get(String name) {
		
		// If we get asked for a name with a dot, that means that the real value was null,
		// and Jexl reverts to Ant-style names where the dot is not an operator but simply part
		// of the name. Since this is not the behavior that we want, we simply return null.
		
		if (name.indexOf('.') != -1)
			//throw new RuntimeException("No such property: " + name);
			return null;
		
		MetaProperty metaProp = objectState.getMetaEntity().getMetaProperty(name);
		if (metaProp == null)
			throw new RuntimeException("Entity " + objectState.getMetaEntity().getEntityName() +
					" does not have an attribute: " + name);
		
		Object value = super.get(name);
		
		// If we've just traversed a relationship and the destination object is not null,
		// wrap it in a BeanMapContext so that we can handle null values properly.
		if (metaProp.isRelationship() && value != null && handleNullRelationships) {
			MetaRole role = (MetaRole)metaProp;
			PersistentBean persBean = HibPersistentBeanFactory.getInstance(logicContext.getSession()).
				createPersistentBeanFromEntity(value, role.getOtherMetaEntity().getEntityName());
			return new BeanMapContext(persBean, logicContext, handleNullRelationships);
		}
		
		// If we're traversing a relationship, and we hit a null, we return a special map that
		// will return a null object of the proper type for any attribute request.
		// We only do that for formulas though, because aggregates and constraints need to handle
		// null values normally.
		if (metaProp.isRelationship() && value == null && handleNullRelationships) {
			
			return new NullObjectMap(((MetaRole)metaProp).getOtherMetaEntity());
		}
		
		// Jexl cannot handle comparisons between an attribute of type char and a string,
		// so all chars are converted to a String
		if (value != null && (value instanceof Character))
			return value.toString();
		
		if (value == null)
			return convertNull(objectState.getMetaEntity(), name);
		
		// Otherwise we just return the requested value
		return value;
	}
	
	/**
	 * If an attribute is null, this returns:
	 * <ul>
	 * <li>numeric: 0
	 * <li>boolean: false
	 * <li>string or char: empty string
	 * <li>anything else: null
	 * </ul>
	 * @param metaEntity The meta entity who owns the property
	 * @param propName The name of the property being retrieved
	 */
	public static Object convertNull(MetaEntity metaEntity, String propName) {
		MetaProperty prop = metaEntity.getMetaProperty(propName);
		if (prop != null && prop.isAttribute()) {
			MetaAttribute att = (MetaAttribute)prop;
			Class<?> attType = att.getType();
			if (Boolean.class.equals(attType))
				return Boolean.FALSE;
			if (Number.class.isAssignableFrom(attType)) {
				return NumberUtil.convertNumberToType(0, attType);
			}
			if (String.class.equals(attType))
				return "";
			if (Character.class.equals(attType))
				return "";
		}
		return null;
	}
	
	/**
	 * An instance of this class is returned to Jexl whenever an expression attempts to traverse
	 * a null relationship. Because this is a Map, Jexl will know to ask it for its attributes, and
	 * this will always return a null (or null equivalent) attribute value.
	 */
	public static class NullObjectMap extends HashMap<String, Object> {
		
		private MetaEntity metaEntity;
		
		protected NullObjectMap(MetaEntity metaEntity) {
			this.metaEntity = metaEntity;
		}
		
		@Override
		public Object get(Object name) {
			return convertNull(metaEntity, (String)name);
		}

		private static final long serialVersionUID = 1;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@Override
	public String toString() {
		return "BeanMapContext for : " + objectState.getEntityName() + "[" + objectState.getPk() + "]";
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  BeanMapContext.java 1207 2012-04-19 22:33:25Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 