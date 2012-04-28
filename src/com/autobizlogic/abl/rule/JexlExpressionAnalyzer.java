package com.autobizlogic.abl.rule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.ExpressionImpl;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.JexlException;

import com.autobizlogic.abl.logic.analysis.ClassDependency;
import com.autobizlogic.abl.logic.analysis.LogicAnalysisManager;
import com.autobizlogic.abl.logic.analysis.PropertyDependency;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaProperty;
import com.autobizlogic.abl.metadata.MetaRole;

/**
 * Figure out the dependencies from a Jexl expression.
 * This depends on a new API in Jexl 2.1, namely JexlEngine.getVariables(Script).
 */
public class JexlExpressionAnalyzer {
	
	/**
	 * Analyze the given expression and return all the dependencies found in it.
	 * @param expression The expression, e.g. <code>customer.balance != total</code>
	 * @param metaEntity The meta entity from which the expression starts
	 * @param methodName The name of the method that defines the logic, for better error reporting.
	 * @return The dependencies
	 */
	public static Map<ClassDependency, List<PropertyDependency>>
		getDependenciesFromExpression(String expression, MetaEntity metaEntity, String methodName) {
		
		Map<ClassDependency, List<PropertyDependency>> dependencies = 
				new HashMap<ClassDependency, List<PropertyDependency>>();
		
		expression = expression.replaceAll("([^=!<>])=([^=])", "$1==$2");
		
		JexlEngine jexlEngine = new JexlEngine();
		Expression expr = null;
		try {
			expr = jexlEngine.createExpression(expression);
		}
		catch(JexlException ex) {
			throw new RuntimeException("Logic method " + methodName + " for " + 
					metaEntity.getEntityName() + " has expression (" + expression + 
					") which has invalid syntax.", ex);
		}
		Set<List<String>> variableSet = jexlEngine.getVariables((ExpressionImpl)expr);
		
		for (List<String> var : variableSet) {
			if (var.size() > 2)
				throw new RuntimeException("Logic method " + methodName + " for " + 
						metaEntity.getEntityName() + " has expression (" + expression + 
						") which contains an invalid path -- only one dereference allowed per term.");
			
			if (var.size() == 1) {
				String attName = var.get(0);
				
				MetaProperty metaProp = metaEntity.getMetaProperty(attName);
				if (metaProp == null)
					throw new RuntimeException("Logic method " + methodName + " for " + 
							metaEntity.getEntityName() + " has expression (" + expression + 
							") which refers to property " + attName + ", which does not seem to exist.");
				addDependency(dependencies, metaEntity.getEntityName(), attName, null, metaEntity.getMetaModel());
			}
			else if (var.size() == 2) {
				String roleName = var.get(0);
				MetaRole metaRole = metaEntity.getMetaRole(roleName);
				if (metaRole == null)
					throw new RuntimeException("Logic method " + methodName + " for " + 
							metaEntity.getEntityName() + " has expression (" + expression + 
							") which refers to relationship " + roleName + ", which does not seem to exist.");
				if (metaRole.isCollection())
					throw new RuntimeException("Logic method " + methodName + " for " + 
							metaEntity.getEntityName() + " has expression (" + expression + 
							") which refers to relationship " + roleName + " which is a collection.");
								
				MetaEntity parentEntity = metaRole.getOtherMetaEntity();
				String attName = var.get(1);
				MetaProperty metaProp = parentEntity.getMetaProperty(attName);
				if (metaProp == null)
					throw new RuntimeException("Logic method " + methodName + " for " + 
							metaEntity.getEntityName() + " has expression (" + expression + 
							") which refers to attribute " + attName + 
							" which does not exist.");
//				if ( ! (metaProp instanceof MetaAttribute))
//					throw new RuntimeException("Logic method " + methodName + " for " + 
//							metaEntity.getEntityName() + " has expression (" + expression + 
//							") which refers to property " + attName + 
//							" which is an entity or collection, and not an attribute.");
				addDependency(dependencies, parentEntity.getEntityName(), attName, roleName, metaEntity.getMetaModel());
			}
		}
		
		return dependencies;
	}
	
	/**
	 * Add a dependency to a Map of them.
	 * @param dependencies The collection of dependencies to add to
	 * @param className The name of the class
	 * @param attributeName The name of the attribute
	 * @param roleName The name of the role (if any)
	 * @param metaModel The meta model in which this all happens
	 */
	private static void addDependency(Map<ClassDependency, List<PropertyDependency>> dependencies,
			String className, String attributeName, String roleName, MetaModel metaModel) {
		
		LogicAnalysisManager lam = LogicAnalysisManager.getInstance(metaModel);
		ClassDependency clsDep = lam.getDependencyForClass(className);
		List<PropertyDependency> propDeps = dependencies.get(clsDep);
		if (propDeps == null) {
			propDeps = new Vector<PropertyDependency>();
			dependencies.put(clsDep, propDeps);
		}
		PropertyDependency propDep = clsDep.getOrCreatePropertyDependency(attributeName, roleName);
		if ( ! propDeps.contains(propDep))
			propDeps.add(propDep);
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 