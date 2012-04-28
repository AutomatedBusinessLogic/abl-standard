package com.autobizlogic.abl.rule;

import java.util.HashSet;
import java.util.Set;

import com.autobizlogic.abl.logic.analysis.AnnotationEntry;
import com.autobizlogic.abl.logic.analysis.LogicClassAnalysis;
import com.autobizlogic.abl.logic.analysis.LogicMethodAnalysis;
import com.autobizlogic.abl.logic.analysis.PropertyDependency;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.rule.MinMaxRule.MinMaxType;
import com.autobizlogic.abl.util.StringUtil;

/**
 * Create the aggregates for a given LogicGroup. This class is tightly bound to LogicGroup and exists
 * only because LogicGroup was becoming unmanageably large.
 */
/* package */ class AggregateMaker {
	
	private LogicGroup logicGroup;
	
	AggregateMaker(LogicGroup logicGroup) {
		this.logicGroup = logicGroup;
	}

	/**
	 * Go over all the methods and select those that define sums or counts, and create the corresponding
	 * objects.
	 */
	/* package */ Set<AbstractAggregateRule> createAggregates() {
		if (logicGroup.aggregates != null)
			return logicGroup.aggregates;

		Set<AbstractAggregateRule> newAggregates = new HashSet<AbstractAggregateRule>();

		LogicClassAnalysis classAnalysis = logicGroup.lam.getLogicAnalysisForEntity(logicGroup.metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() == LogicMethodAnalysis.Type.SUM) {
				SumRule theSum = createSum(methodAnalysis);
				if (theSum != null)
					newAggregates.add(theSum);
			}
			else if (methodAnalysis.getType() == LogicMethodAnalysis.Type.COUNT) {
				CountRule theCount = createCount(methodAnalysis);
				if (theCount != null)
					newAggregates.add(theCount);
			}
			else if (methodAnalysis.getType() == LogicMethodAnalysis.Type.MINIMUM ||
					methodAnalysis.getType() == LogicMethodAnalysis.Type.MAXIMUM) {
				MinMaxRule theMinMax = createMinMax(methodAnalysis);
				if (theMinMax != null)
					newAggregates.add(theMinMax);
			}
		}
		
		// Assign it to the instance variable, which signals everyone that this is ready to go
		return newAggregates;
	}

	/**
	 * Create one sum rule from a method analysis.
	 * @param methodAnalysis
	 * @return
	 */
	private SumRule createSum(LogicMethodAnalysis methodAnalysis) {
		// First figure out which bean attribute this count is for
		String beanAttributeName = logicGroup.getBeanAttributeName(methodAnalysis, "Sum");

		AnnotationEntry sumAnnotation = methodAnalysis.getAnnotations().get("Sum");

		String value = StringUtil.stripSurroundingDoubleQuotes((String)sumAnnotation.parameters.get("value"));
		if (value == null || value.trim().length() == 0)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a count, but it has no specification (e.g. rolename where condition)");
		value = value.trim();
		
		String roleName = null;
		String summedAttrib = null;
		String clause = null;
		int whereIdx = value.toUpperCase().indexOf(" WHERE ");
		if (whereIdx > -1) {
			clause = value.substring(whereIdx + 6).trim();
			value = value.substring(0, whereIdx).trim();
		}
		int dotIdx = value.indexOf('.');
		if (dotIdx == -1)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a sum, but the syntax in the annotation is incorrect, it should be rolename.attributename, " +
			"optionally followed by a where condition.");
		roleName = value.substring(0, dotIdx).trim();
		summedAttrib = value.substring(dotIdx + 1).trim();

		if (roleName == null || roleName.length() == 0)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a sum, but there is no role name specified");
		if (summedAttrib == null || summedAttrib.length() == 0)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a sum, but there is no summed attribute specified");

		// TODO Verify that the summedAttribute does exist... if not in bean (available here?), then it is perhaps transient
		//if ( ! BeanChecker.beanHasAttribute(summedAttrib, attributeName))

		// Verify that the role exists
		MetaRole metaRole = logicGroup.metaEntity.getMetaRole(roleName);
		if (metaRole == null)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a sum, but the specified role name " + roleName + " does not seem to exist in the bean.");

		SumRule sum = new SumRule(logicGroup, methodAnalysis.getMethodName(), roleName, clause, summedAttrib, beanAttributeName);
		if (methodAnalysis.getCodeSize() == 1)
			sum.setNoCode(true);

		Boolean inMemory = (Boolean)sumAnnotation.parameters.get("inMemory");
		if (inMemory != null)
			sum.setInMemory(inMemory);
		Boolean persistent = (Boolean)sumAnnotation.parameters.get("persistent");
		if (persistent != null )
			sum.setPersistent(persistent);

		// Add it to the lookup table
		RuleManager.getInstance(logicGroup.metaModel).addDerivationRuleForAttribute(
				logicGroup.metaEntity.getEntityName(), beanAttributeName, sum);

		// Make sure no other rule derives the value of this attribute
		if (logicGroup.derivations.containsKey(beanAttributeName)) {
			AbstractRule rule = logicGroup.derivations.get(beanAttributeName);
			throw new RuntimeException("Sum " + logicGroup.getLogicClassName() + "." + methodAnalysis.getMethodName() +
					" is supposed to derive the value of attribute " + beanAttributeName + ", but that attribute's value " +
					"is also derived by " + rule.getLogicMethodName() + ". An attribute can be derived only by one method.");
		}
		logicGroup.derivations.put(beanAttributeName, sum);

		// Remember that this method is deriving this attribute
		logicGroup.methodDerivationMap.put(methodAnalysis.getMethodName(), beanAttributeName);
		
		// Now add the dependencies. First for the relationship
		RuleDependency depend = new RuleDependency(metaRole.getOtherMetaEntity().getEntityName(),
				summedAttrib, metaRole.getRoleName());
		sum.addDependency(depend);

		// Then for the qualifying expression, if any
		if (clause != null && clause.trim().length() > 0) {
			Set<PropertyDependency> propDepends = sum.addDependenciesFromExpression(clause, 
					metaRole.getOtherMetaEntity(), metaRole.getRoleName(), methodAnalysis.getMethodName());
			logicGroup.dependencies.put(sum, propDepends);
		}

		return sum;
	}

	/**
	 * Create one count rule from a method analysis.
	 * @param methodAnalysis The method analysis for the method that defines the count
	 * @return The new CountRule
	 */
	private CountRule createCount(LogicMethodAnalysis methodAnalysis) {
		// First figure out which bean attribute this count is for
		String beanAttributeName = logicGroup.getBeanAttributeName(methodAnalysis, "Count");

		AnnotationEntry countAnnotation = methodAnalysis.getAnnotations().get("Count");
		
		String value = StringUtil.stripSurroundingDoubleQuotes((String)countAnnotation.parameters.get("value"));
		if (value == null || value.trim().length() == 0)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a count, but it has no specification (e.g. rolename where condition)");
		value = value.trim();
		
		String roleName = null;
		String clause = null;
		int whereIdx = value.toUpperCase().indexOf(" WHERE ");
		if (whereIdx > -1) {
			clause = value.substring(whereIdx + 6).trim();
			value = value.substring(0, whereIdx);
		}
		roleName = value;
		
		if (roleName == null || roleName.length() == 0)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a count, but there is no role name specified");
		
		CountRule cnt = new CountRule(logicGroup, methodAnalysis.getMethodName(), roleName, clause, beanAttributeName);
		if (methodAnalysis.getCodeSize() == 1)
			cnt.setNoCode(true);

		Boolean inMemory = (Boolean)countAnnotation.parameters.get("inMemory");
		if (inMemory != null && inMemory)
			cnt.setInMemory(true);
		Boolean persistent = (Boolean)countAnnotation.parameters.get("persistent");
		if (persistent != null && !persistent)
			cnt.setPersistent(false);

		// Verify that the role exists
		MetaRole metaRole = logicGroup.metaEntity.getMetaRole(roleName);
		if (metaRole == null)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a count, but its roleName " + roleName + " does not seem to exist in the bean.");

		// Add it to the lookup table
		RuleManager.getInstance(logicGroup.metaModel).addDerivationRuleForAttribute(
				logicGroup.metaEntity.getEntityName(), beanAttributeName, cnt);

		// Make sure no other rule derives the value of this attribute
		if (logicGroup.derivations.containsKey(beanAttributeName)) {
			AbstractRule rule = logicGroup.derivations.get(beanAttributeName);
			throw new RuntimeException("Count " + logicGroup.getLogicClassName() + "." + methodAnalysis.getMethodName() +
					" is supposed to derive the value of attribute " + beanAttributeName + ", but that attribute's value " +
					"is also derived by " + rule.getLogicMethodName() + ". An attribute can be derived only by one method.");
		}
		logicGroup.derivations.put(beanAttributeName, cnt);

		// Remember that this method is deriving this attribute
		logicGroup.methodDerivationMap.put(methodAnalysis.getMethodName(), beanAttributeName);
		
		// And add the dependencies to the rule
		
		// First for the relationship
		RuleDependency depend = new RuleDependency(metaRole.getOtherMetaEntity().getEntityName(),
				metaRole.getOtherMetaRole().getRoleName(), metaRole.getRoleName());
		cnt.addDependency(depend);
		
		// Then for the qualifying expression, if any
		if (clause != null && clause.trim().length() > 0) {
			Set<PropertyDependency> propDepends = cnt.addDependenciesFromExpression(clause, 
					metaRole.getOtherMetaEntity(), metaRole.getRoleName(), methodAnalysis.getMethodName());
			logicGroup.dependencies.put(cnt, propDepends);
		}

		return cnt;
	}

	private MinMaxRule createMinMax(LogicMethodAnalysis methodAnalysis) {
		
		String minMaxTypeName = "Maximum";
		MinMaxType minMaxType = MinMaxType.MAX;
		if (methodAnalysis.getType() == LogicMethodAnalysis.Type.MINIMUM) {
			minMaxTypeName = "Minimum";
			minMaxType = MinMaxType.MIN;
		}

		// First figure out which bean attribute this count is for
		String beanAttributeName = logicGroup.getBeanAttributeName(methodAnalysis, minMaxTypeName);

		AnnotationEntry minMaxAnnotation = methodAnalysis.getAnnotations().get(minMaxTypeName);
		
		String value = StringUtil.stripSurroundingDoubleQuotes((String)minMaxAnnotation.parameters.get("value"));
		if (value == null || value.trim().length() == 0)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a minimum/maximum, but it has no specification (e.g. rolename where condition)");
		value = value.trim();
		
		String roleName = null;
		String summedAttrib = null;
		String clause = null;
		int whereIdx = value.toUpperCase().indexOf(" WHERE ");
		if (whereIdx > -1) {
			clause = value.substring(whereIdx + 6).trim();
			value = value.substring(0, whereIdx).trim();
		}
		int dotIdx = value.indexOf('.');
		if (dotIdx == -1)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a sum, but the syntax in the annotation is incorrect, it should be rolename.attributename, " +
			"optionally followed by a where condition.");
		roleName = value.substring(0, dotIdx).trim();
		summedAttrib = value.substring(dotIdx + 1).trim();

		MinMaxRule minMaxRule = new MinMaxRule(logicGroup, methodAnalysis.getMethodName(), roleName, 
				clause, summedAttrib, beanAttributeName, minMaxType);
		if (methodAnalysis.getCodeSize() == 1)
			minMaxRule.setNoCode(true);

		Boolean inMemory = (Boolean)minMaxAnnotation.parameters.get("inMemory");
		if (inMemory != null && inMemory)
			minMaxRule.setInMemory(true);
		Boolean persistent = (Boolean)minMaxAnnotation.parameters.get("persistent");
		if (persistent != null && !persistent)
			minMaxRule.setPersistent(false);

		// Verify that the role exists
		MetaRole metaRole = logicGroup.metaEntity.getMetaRole(roleName);
		if (metaRole == null)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a count, but its roleName " + roleName + " does not seem to exist in the bean.");

		// Add it to the lookup table
		RuleManager.getInstance(logicGroup.metaModel).addDerivationRuleForAttribute(
				logicGroup.metaEntity.getEntityName(), beanAttributeName, minMaxRule);

		// Make sure no other rule derives the value of this attribute
		if (logicGroup.derivations.containsKey(beanAttributeName)) {
			AbstractRule rule = logicGroup.derivations.get(beanAttributeName);
			throw new RuntimeException("Count " + logicGroup.getLogicClassName() + "." + methodAnalysis.getMethodName() +
					" is supposed to derive the value of attribute " + beanAttributeName + ", but that attribute's value " +
					"is also derived by " + rule.getLogicMethodName() + ". An attribute can be derived only by one method.");
		}
		logicGroup.derivations.put(beanAttributeName, minMaxRule);

		// Remember that this method is deriving this attribute
		logicGroup.methodDerivationMap.put(methodAnalysis.getMethodName(), beanAttributeName);
		
		// And add the dependencies to the rule
		
		// First for the relationship
		RuleDependency depend = new RuleDependency(metaRole.getOtherMetaEntity().getEntityName(),
				metaRole.getOtherMetaRole().getRoleName(), metaRole.getRoleName());
		minMaxRule.addDependency(depend);
		
		// Then for the qualifying expression, if any
		if (clause != null && clause.trim().length() > 0) {
			Set<PropertyDependency> propDepends = minMaxRule.addDependenciesFromExpression(clause, 
					metaRole.getOtherMetaEntity(), metaRole.getRoleName(), methodAnalysis.getMethodName());
			logicGroup.dependencies.put(minMaxRule, propDepends);
		}

		return minMaxRule;
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
 