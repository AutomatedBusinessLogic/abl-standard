package com.autobizlogic.abl.mgmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.hibernate.SessionFactory;

import com.autobizlogic.abl.hibernate.HibernateConfiguration;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaModelFactory;
import com.autobizlogic.abl.rule.*;

/**
 * The management service for dependencies.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class DependencyService {

	public static Map<String, Object> service(Map<String, String> args) {
		
		String serviceName = args.get("service");
		if (serviceName.equals("getDependenciesForRule"))
			return getDependenciesForClass(args);

		return null;
	}

	public static Map<String, Object> getDependenciesForClass(Map<String, String> args) {
		String className = args.get("className");
		String sessionFactoryId = args.get("sessionFactoryId");
		HashMap<String, Object> result = new HashMap<String, Object>();
		SessionFactory sessionFactory = HibernateConfiguration.getSessionFactoryById(sessionFactoryId);
		if (sessionFactory == null)
			return null;
		MetaModel metaModel = MetaModelFactory.getHibernateMetaModel(sessionFactory);

		Map<String, Object> rules = new HashMap<String, Object>();
		result.put("rules", rules);
		LogicGroup logicGroup = RuleManager.getInstance(metaModel).getLogicGroupForEntityName(className);
		Set<AbstractRule> allRules = logicGroup.getAllRules();
		for (AbstractRule rule : allRules) {
			Map<String, Object> ruleDep = new HashMap<String, Object>();
			rules.put(rule.getLogicMethodName(), ruleDep);
			if (rule instanceof FormulaRule) {
				ruleDep.put("attribute", ((FormulaRule)rule).getBeanAttributeName());
			}
			if (rule instanceof AbstractAggregateRule) {
				ruleDep.put("attribute", ((AbstractAggregateRule)rule).getBeanAttributeName());
			}
			if (rule instanceof ParentCopyRule) {
				ruleDep.put("attribute", ((ParentCopyRule)rule).getBeanAttributeName());
			}
			ruleDep.put("type", rule.getClass().getName());
			Set<RuleDependency> ruleDependencies = rule.getDependencies(); // logicGroup.getDependenciesForRule(rule);
			List<Map<String, String>> dependsList = new Vector<Map<String, String>>();
			ruleDep.put("dependencies", dependsList);
			for (RuleDependency ruleDependency : ruleDependencies) {
				Map<String, String> depEntry = new HashMap<String, String>();
				dependsList.add(depEntry);
				depEntry.put("className", ruleDependency.getBeanClassName());
				depEntry.put("attributeName", ruleDependency.getBeanAttributeName());
				depEntry.put("roleName", ruleDependency.getBeanRoleName());
			}
		}
		
		return result;
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
 