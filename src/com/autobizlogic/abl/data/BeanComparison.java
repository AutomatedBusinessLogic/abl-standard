package com.autobizlogic.abl.data;

import java.math.BigDecimal;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.impl.SessionImpl;

import com.autobizlogic.abl.hibernate.HibernateSessionUtil;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.text.LogicMessageFormatter;
import com.autobizlogic.abl.text.MessageName;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.NumberUtil;

/**
 * Compare two persistent beans and figure out how they're different.
 */
public class BeanComparison {

	/**
	 * Get a human-readable string showing the difference(s) (if any) between the two
	 * given beans.
	 */
	public static String comparePersistentBeans(PersistentBean bean1, PersistentBean bean2, Session session) {
		StringBuffer sb = new StringBuffer();
		
		if (bean1 == null && bean2 == null)
			return LogicMessageFormatter.getMessage(MessageName.data_BeanComparison_bothBeansNull);
		if (bean1 == null && bean2 != null)
			return LogicMessageFormatter.getMessage(MessageName.data_BeanComparison_oneBeanNull, 
					new Object[]{bean2});
		if (bean1 != null && bean2 == null)
			return LogicMessageFormatter.getMessage(MessageName.data_BeanComparison_oneBeanNull, 
					new Object[]{bean1});
		
		MetaEntity metaEntity1 = bean1.getMetaEntity();
		MetaEntity metaEntity2 = bean2.getMetaEntity();
		if ( ! metaEntity1.equals(metaEntity2))
			return LogicMessageFormatter.getMessage(MessageName.data_BeanComparison_differentEntities, 
					new Object[]{bean1, bean2});
		
		sb.append(" [");
		
		boolean isFirstAttr = true;
		Set<MetaAttribute> metaAttributes = metaEntity1.getMetaAttributes();
		for (MetaAttribute metaAttribute : metaAttributes) {
			if (! isFirstAttr) 
				sb.append(", ");
			isFirstAttr = false;
			
			Object val1 = bean1.get(metaAttribute.getName());
			Object val2 = bean2.get(metaAttribute.getName());
			sb.append(metaAttribute.getName());
			sb.append(": ");
			
			if (val1 == null && val2 == null) {
				sb.append("null");
				continue;
			}
			if (val1 != null && val2 != null && val1.equals(val2)) {
				sb.append(val1);
				continue;
			}
			
			if (val1 == null && val2 != null) {
				sb.append("null");
				sb.append(" (" + val2.toString() + ")");
			}
			else if (val1 != null && val2 == null) {
				sb.append(val1.toString());
				sb.append(" (null)");
			}
			else {
				sb.append(val1.toString());
				sb.append(" (" + val2.toString()+ ")");
			}
		}
		
		Set<MetaRole> metaRoles = metaEntity1.getRolesFromChildToParents();
		for (MetaRole metaRole : metaRoles) {
			Object val1 = bean1.get(metaRole.getRoleName());
			Object val2 = bean2.get(metaRole.getRoleName());
			if (val1 == null && val2 == null)
				continue;
			if (val1 != null & val2 != null) {
				Session realSession = HibernateSessionUtil.getRealSession(session);
				SessionImpl sessionImpl = (SessionImpl)realSession;
				Object pk1 = sessionImpl.getContextEntityIdentifier(val1);
				Object pk2 = sessionImpl.getContextEntityIdentifier(val2);
				if (pk1 != null && pk2 != null && pk1.equals(pk2))
					continue;
			}
			
			sb.append(metaRole.getRoleName());
			sb.append("], ");
			sb.append("[");
			if (val1 == null && val2 != null) {
				sb.append("null/");
				sb.append(BeanUtil.getBeanDescription(metaRole.getOtherMetaEntity(), val2));
			}
			else if (val1 != null && val2 == null) {
				sb.append(BeanUtil.getBeanDescription(metaRole.getOtherMetaEntity(), val1));
				sb.append("/null");
			}
			else {
				sb.append(BeanUtil.getBeanDescription(metaRole.getOtherMetaEntity(), val1));
				sb.append("/");
				sb.append(BeanUtil.getBeanDescription(metaRole.getOtherMetaEntity(), val2));
			}
		}
		
		sb.append("]");
		
		return sb.toString();
	}
	
	/**
	 * Do a deep(ish) comparison of the two beans and determine whether they are truly equal.
	 * This will compare the class and PK, and the values of all attributes and parent relationships.
	 * If both beans are null,this returns false.
	 * <p/>
	 * Note that this is different from PersistentBean's equals, which only compares entity name
	 * and primary key.
	 */
	public static boolean beansAreEqual(PersistentBean bean1, PersistentBean bean2, Session session) {
		
		if (bean1 == null && bean2 == null)
			return false;
		if (bean1 == null && bean2 != null)
			return false;
		if (bean1 != null && bean2 == null)
			return false;
		
		MetaEntity metaEntity1 = bean1.getMetaEntity();
		MetaEntity metaEntity2 = bean2.getMetaEntity();
		if ( ! metaEntity1.equals(metaEntity2))
			return false;
		
		Set<MetaAttribute> metaAttributes = metaEntity1.getMetaAttributes();
		for (MetaAttribute metaAttribute : metaAttributes) {			
			Object val1 = bean1.get(metaAttribute.getName());
			Object val2 = bean2.get(metaAttribute.getName());
			if (val1 == null && val2 == null)
				continue;
			if (val1 != null && val2 != null && val1.equals(val2))
				continue;
			
			// Special case for BigDecimal, which does not compare equal if the scales are
			// not equal (sigh).
			if (val1 != null && val2 != null && (val1 instanceof BigDecimal) && (val2 instanceof BigDecimal)) {
				BigDecimal bd1 = (BigDecimal)val1;
				BigDecimal bd2 = (BigDecimal)val2;
				if (NumberUtil.numbersAreEqual(bd1, bd2))
					continue;
			}
			
			return false;			
		}
		
		Set<MetaRole> metaRoles = metaEntity1.getRolesFromChildToParents();
		for (MetaRole metaRole : metaRoles) {
			Object val1 = bean1.get(metaRole.getRoleName());
			Object val2 = bean2.get(metaRole.getRoleName());
			if (val1 == null && val2 == null)
				continue;
			if (val1 == null || val2 == null)
				return false;
			Session realSession = HibernateSessionUtil.getRealSession(session);
			Object pk1 = ((SessionImpl)realSession).getContextEntityIdentifier(val1);
			Object pk2 = ((SessionImpl)realSession).getContextEntityIdentifier(val2);
			if (pk1 == null || pk2 == null)
				return false;
			return pk1.equals(pk2);
		}
		
		return true;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  BeanComparison.java 1248 2012-04-23 23:28:58Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 