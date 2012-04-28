package com.autobizlogic.abl.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.autobizlogic.abl.annotations.Verbs;
import com.autobizlogic.abl.logic.analysis.AnnotationEntry;
import com.autobizlogic.abl.logic.analysis.LogicClassAnalysis;
import com.autobizlogic.abl.logic.analysis.LogicMethodAnalysis;
import com.autobizlogic.abl.logic.analysis.PropertyDependency;
import com.autobizlogic.abl.util.StringUtil;

/* package */ class ConstraintMaker {

	private LogicGroup logicGroup;
	
	ConstraintMaker(LogicGroup logicGroup) {
		this.logicGroup = logicGroup;
	}

	/**
	 * Go over all the methods, select those that define constraints, and create the corresponding
	 * ConstraintRule objects.
	 */
	/* package */ Set<ConstraintRule> createConstraints() {
		if (logicGroup.constraints != null)
			return logicGroup.constraints;

		// For the dependency analysis to work, we need to figure out all the derived attributes first
		logicGroup.createFormulas();
		logicGroup.createAggregates();

		Set<ConstraintRule> newConstraints = new HashSet<ConstraintRule>();

		LogicClassAnalysis classAnalysis = logicGroup.lam.getLogicAnalysisForEntity(logicGroup.metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.CONSTRAINT)
				continue;

			ConstraintRule constraint = createConstraint(methodAnalysis, false);
			if (constraint != null)
				newConstraints.add(constraint);
		}
		
		return newConstraints;
	}

	/* package */ Set<CommitConstraintRule> createCommitConstraints() {
		if (logicGroup.commitConstraints != null)
			return logicGroup.commitConstraints;

		// For the dependency analysis to work, we need to figure out all the derived attributes first
		logicGroup.createFormulas();
		logicGroup.createAggregates();

		Set<CommitConstraintRule> newCommitConstraints = new HashSet<CommitConstraintRule>();

		LogicClassAnalysis classAnalysis = logicGroup.lam.getLogicAnalysisForEntity(logicGroup.metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.COMMITCONSTRAINT)
				continue;

			CommitConstraintRule constraint = (CommitConstraintRule)createConstraint(methodAnalysis, true);
			if (constraint != null)
				newCommitConstraints.add(constraint);
		}
		
		return newCommitConstraints;
	}

	/**
	 * Create a constraint based on its method analysis. This will analyze the code of the constraint
	 * and check for several things:
	 * <ul>
	 * <li>whether the constraint ever calls ConstraintFailure.failConstraint. If it does not, a warning
	 * will be logged.
	 * <li>if the constraint method has a non-null return type, an exception will be thrown.
	 * <li>if any of the attributes listed in problemAttributes do not actually exist, an exception
	 * will be thrown.
	 * </ul>
	 * @param methodAnalysis The method analysis in question
	 * @param commitTime If true, create a CommitConstraintRule
	 * @return The new constraint object
	 */
	private ConstraintRule createConstraint(LogicMethodAnalysis methodAnalysis, boolean commitTime) {

		// Verify that the method is void
		String returnType = methodAnalysis.getReturnTypeName();
		if (returnType != null && !returnType.equals("void"))
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is defined as a constraint, but it has a non-void return type. " +
					"All constraints must be declared as void.");

		AnnotationEntry annot = methodAnalysis.getAnnotations().get("Constraint");
		if (annot == null)
			annot = methodAnalysis.getAnnotations().get("CommitConstraint");
		if (annot == null)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a constraint, but the corresponding annotation could not be found.");

		// Check the annotation for problemAttributes
		String[] problemAttributes = null;
		String problemAttStr = StringUtil.stripSurroundingDoubleQuotes((String)annot.parameters.get("problemAttributes"));
		if (problemAttStr != null && problemAttStr.trim().length() > 0) {
			String[] problemAtts = problemAttStr.split(",");
			problemAttributes = new String[problemAtts.length];
			int i = 0;
			for (String att : problemAtts) {
				att = att.trim();
				problemAttributes[i++] = att;
				if (logicGroup.metaEntity.getMetaAttribute(att) == null)
					throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
							" which is marked as a constraint. It lists " + att + " as problem attribute, but that attribute " +
							"does not seem to exist in entity " + logicGroup.metaEntity.getEntityName());
			}
		}

		String errorMessage = null;
		String errMsg = StringUtil.stripSurroundingDoubleQuotes((String)annot.parameters.get("errorMessage"));
		if (errMsg != null && errMsg.trim().length() > 0) {
			errorMessage = errMsg;
		}
		
		ConstraintRule constraint;
		if (commitTime)
			constraint = new CommitConstraintRule(logicGroup, methodAnalysis.getMethodName());
		else
			constraint = new ConstraintRule(logicGroup, methodAnalysis.getMethodName());
		constraint.problemAttributes = problemAttributes;
		constraint.setErrorMessage(errorMessage);
		Verbs verbs = (Verbs)annot.parameters.get("verbs");
		if (verbs == null)
			verbs = Verbs.ALL;
		constraint.setVerbs(verbs);
		

		String expression = (String)annot.parameters.get("value");
		if (expression != null) {
			String expr = StringUtil.stripSurroundingDoubleQuotes(expression);
			expr = expr.replaceAll("([^=!<>])=([^=])", "$1==$2");
			constraint.setExpression(expr);
			
			// Add the dependencies for the expression
			Set<PropertyDependency> propDepends = 
					constraint.addDependenciesFromExpression(expr, logicGroup.metaEntity, null, methodAnalysis.getMethodName());
			logicGroup.dependencies.put(constraint, propDepends);
		}

		// Now add the dependencies from the code
		Set<PropertyDependency> propDepends = new HashSet<PropertyDependency>();
		for (List<PropertyDependency> deps : methodAnalysis.getDependencies().values()) {
			propDepends.addAll(deps);
		}
		logicGroup.dependencies.put(constraint, propDepends);
		Set<RuleDependency> depends = logicGroup.getDependenciesForRule(constraint);
		for (RuleDependency dep : depends)
			constraint.addDependency(dep);

		return constraint;
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
 