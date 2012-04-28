package com.autobizlogic.abl.mgmt;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javassist.util.proxy.ProxyFactory;

import org.hibernate.EntityMode;
import org.hibernate.SessionFactory;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.AssociationType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.Type;

import com.autobizlogic.abl.hibernate.HibernateConfiguration;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.rule.*;

/**
 * The management service for class metadata.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class ClassMetadataService {
	
	public static Map<String, Object> service(Map<String, String> args) {
		
		String serviceName = args.get("service");
		if (serviceName.equals("getAllPersistentBeans"))
			return getAllPersistentBeans(args);
		if (serviceName.equals("getMetadataForClass"))
			return getMetadataForClass(args);

		return null;
	}

	public static Map<String, Object> getAllPersistentBeans(Map<String, String> args) {
		String sessionFactoryId = args.get("sessionFactoryId");
		HashMap<String, Object> result = new HashMap<String, Object>();
		SessionFactory sessionFactory = HibernateConfiguration.getSessionFactoryById(sessionFactoryId);
		if (sessionFactory == null)
			return null;
		
		// Sort the result
		List<String> sorted = new Vector<String>(sessionFactory.getAllClassMetadata().keySet());
		Collections.sort(sorted);
		
		result.put("data", sorted);
		return result;
	}

	public static Map<String, Object> getMetadataForClass(Map<String, String> args) {
		String sessionFactoryId = args.get("sessionFactoryId");
		String name = args.get("className");
		HashMap<String, Object> result = new HashMap<String, Object>();
		
		SessionFactory factory = HibernateConfiguration.getSessionFactoryById(sessionFactoryId);
		if (factory == null)
			return null;
		
		ClassMetadata meta = factory.getClassMetadata(name);
		if (meta == null)
			return null;
		
		result.put("sessionFactoryId", sessionFactoryId);
		result.put("className", meta.getEntityName());
		result.put("identifierPropertyName", meta.getIdentifierPropertyName());
		
		Class<?> cls = meta.getMappedClass(EntityMode.POJO);
		Class<?> supercls = cls.getSuperclass();
		while (ProxyFactory.isProxyClass(supercls))
			supercls = supercls.getSuperclass();
		result.put("superclassName", supercls.getName());
		
		Map<String, String> properties = new HashMap<String, String>();
		Map<String, Object> collections = new HashMap<String, Object>();
		Map<String, String> associations = new HashMap<String, String>();
		
		String[] propNames = meta.getPropertyNames();
		Type[] propTypes = meta.getPropertyTypes();
		int i = 0;
		for (String propName : propNames) {
			if (propTypes[i].isCollectionType()) {
				CollectionType collType = (CollectionType)propTypes[i];
				Type elementType = collType.getElementType((SessionFactoryImplementor)factory);
				HashMap<String, String> collEntry = new HashMap<String, String>();
				collEntry.put("collectionType", collType.getReturnedClass().getName());
				collEntry.put("elementType", elementType.getName());
				collections.put(propName, collEntry);
			}
			else if (propTypes[i].isAssociationType()) {
				AssociationType assType = (AssociationType)propTypes[i];
				String assName = assType.getAssociatedEntityName((SessionFactoryImplementor)factory);
				associations.put(propName, assName);
			}
			else {
				properties.put(propName, propTypes[i].getName());
			}
			i++;
		}
		result.put("properties", properties);
		result.put("associations", associations);
		result.put("collections", collections);
		
		MetaModel metaModel = MetaModelFactory.getHibernateMetaModel(factory);
		LogicGroup logicGroup = RuleManager.getInstance(metaModel).getLogicGroupForClassName(name);
		if (logicGroup != null) {
			
			// Operations are actually actions and constraints
			List<Map<String, Object>> operations = new Vector<Map<String, Object>>();
			result.put("operations", operations);
			
			Set<ActionRule> actions = logicGroup.getActions();
			if (actions != null && actions.size() > 0) {
				for (ActionRule a : actions) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("name", a.getLogicMethodName());
					op.put("type", "action");
					operations.add(op);
				}
			}
			
			Set<EarlyActionRule> eactions = logicGroup.getEarlyActions();
			if (eactions != null && eactions.size() > 0) {
				for (EarlyActionRule a : eactions) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("name", a.getLogicMethodName());
					op.put("type", "early action");
					operations.add(op);
				}
			}
			
			Set<CommitActionRule> cactions = logicGroup.getCommitActions();
			if (cactions != null && cactions.size() > 0) {
				for (CommitActionRule a : cactions) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("name", a.getLogicMethodName());
					op.put("type", "commit action");
					operations.add(op);
				}
			}
			
			Set<ConstraintRule> constraints = logicGroup.getConstraints();
			if (constraints != null && constraints.size() > 0) {
				for (ConstraintRule constraint : constraints) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("name", constraint.getLogicMethodName());
					op.put("type", "constraint");
					operations.add(op);
				}
			}
			
			Set<CommitConstraintRule> cconstraints = logicGroup.getCommitConstraints();
			if (cconstraints != null && cconstraints.size() > 0) {
				for (ConstraintRule cconstraint : cconstraints) {
					Map<String, Object> op = new HashMap<String, Object>();
					op.put("name", cconstraint.getLogicMethodName());
					op.put("type", "commit constraint");
					operations.add(op);
				}
			}
			
			// Derivations are derived attributes
			Map<String, Object> derivations = new HashMap<String, Object>();
			result.put("derivations", derivations);
			
			Set<AbstractAggregateRule> aggregates =  logicGroup.getAggregates();
			if (aggregates != null && aggregates.size() > 0) {
				for (AbstractAggregateRule aggregate : aggregates) {
					Map<String, Object> agg = new HashMap<String, Object>();
					if (aggregate instanceof CountRule)
						agg.put("type", "count");
					else if (aggregate instanceof SumRule)
						agg.put("type", "sum");
					else
						agg.put("type", "unknown");
					agg.put("methodName", aggregate.getLogicMethodName());
					derivations.put(aggregate.getBeanAttributeName(), agg);
				}
			}
			
			List<FormulaRule> formulas = logicGroup.getFormulas();
			if (formulas != null && formulas.size() > 0) {
				for (FormulaRule formula : formulas) {
					Map<String, Object> form = new HashMap<String, Object>();
					form.put("type", "formula");
					form.put("methodName", formula.getLogicMethodName());
					derivations.put(formula.getBeanAttributeName(), form);
				}
			}
			
			Set<ParentCopyRule> pcRules = logicGroup.getParentCopies();
			if (pcRules != null && pcRules.size() > 0) {
				for (ParentCopyRule pcRule : pcRules) {
					Map<String, Object> parentCopy = new HashMap<String, Object>();
					parentCopy.put("type", "parent copy");
					parentCopy.put("methodName", pcRule.getLogicMethodName());
					derivations.put(pcRule.getChildAttributeName(), parentCopy);
				}
			}
		}
		
		HashMap<String, Object> finalResult = new HashMap<String, Object>();
		finalResult.put("data", result);
		
		return finalResult;
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 