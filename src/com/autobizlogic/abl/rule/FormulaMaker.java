package com.autobizlogic.abl.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.autobizlogic.abl.logic.analysis.AnnotationEntry;
import com.autobizlogic.abl.logic.analysis.ClassDependency;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.logic.analysis.LogicAnalysisManager;
import com.autobizlogic.abl.logic.analysis.LogicClassAnalysis;
import com.autobizlogic.abl.logic.analysis.LogicMethodAnalysis;
import com.autobizlogic.abl.logic.analysis.PropertyDependency;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.util.StringUtil;

/* package */ class FormulaMaker {

	private LogicGroup logicGroup;
	
	FormulaMaker(LogicGroup logicGroup) {
		this.logicGroup = logicGroup;
	}

	/**
	 * Analyze the logic and create the formulas out of it.
	 */
	/* package */ Set<FormulaRule> createFormulas() {

		// If we've already done this, no point in doing it again
		if (logicGroup.formulas != null)
			return logicGroup.formulas;

		Set<FormulaRule> newFormulas = new HashSet<FormulaRule>();

		LogicClassAnalysis classAnalysis = logicGroup.lam.getLogicAnalysisForEntity(logicGroup.metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.FORMULA)
				continue;

			FormulaRule theFormula = createFormula(methodAnalysis);
			if (theFormula != null)
				newFormulas.add(theFormula);
		}
		
		// Finally set the value. This is the last step because it signals to other
		// threads that the data is ready.
		return newFormulas;
	}

	/**
	 * Create a formula based on a method analysis.
	 * @param methodAnalysis The method analysis
	 * @return The formula
	 */
	private FormulaRule createFormula(LogicMethodAnalysis methodAnalysis) {
		// First figure out which bean attribute this formula is for
		String attributeName = logicGroup.getBeanAttributeName(methodAnalysis, "Formula");

		// Now create the formula, with all required information
		FormulaRule formula = new FormulaRule(logicGroup, methodAnalysis.getMethodName(), attributeName);

		AnnotationEntry formulaAnnotation = methodAnalysis.getAnnotations().get("Formula");
		Boolean lazy = (Boolean)formulaAnnotation.parameters.get("lazy");
		if (lazy != null && lazy)
			formula.setLazy(true);
		Boolean persistent = (Boolean)formulaAnnotation.parameters.get("persistent");
		if (persistent != null)
			formula.setPersistent(persistent);
		String expression = (String)formulaAnnotation.parameters.get("value");
		if (expression != null) {
			String expr = StringUtil.stripSurroundingDoubleQuotes(expression);
			expr = expr.replaceAll("([^=!<>])=([^=])", "$1==$2");
			formula.setExpression(expr);
		}
		Boolean skipDuringRecompute = (Boolean)formulaAnnotation.parameters.get("skipDuringRecompute");
		if (skipDuringRecompute != null)
			formula.setSkipDuringRecompute(skipDuringRecompute);
		Boolean pruning = (Boolean)formulaAnnotation.parameters.get("pruning");
		if (pruning != null)
			formula.setPruning(pruning);

		// Verify that said attribute is the same type as the return type of the method
		String returnType = methodAnalysis.getReturnTypeName();
		if ((returnType == null || "void".equals(returnType)) && expression == null)
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a formula, but does not return anything");
		
		if (expression != null && returnType != null && !"void".equals(returnType)) {
			throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is declared as a formula with a value in the annotation, but it has a non-void return type. " +
					"This is not allowed because the return value is ignored when the formula is declared in the annotation.");
		}
		
		if (expression == null) {
			MetaAttribute metaAttrib = logicGroup.metaEntity.getMetaAttribute(attributeName); // Will not be null -- see getBeanAttributeName
			Class<?> returnClass = ClassLoaderManager.getInstance().getClassFromName(returnType);
			Class<?> attribClass = metaAttrib.getType();
			if ( ! attribClass.isAssignableFrom(returnClass))
				throw new RuntimeException("Logic class " + logicGroup.logicClassName + " has method " + methodAnalysis.getMethodName() +
						" which is marked as a formula, but its return type does not match the type of the corresponding " +
						"bean attribute " + attributeName);
		}

		// Add it to the lookup table
		RuleManager.getInstance(logicGroup.metaModel).addDerivationRuleForAttribute(
				logicGroup.metaEntity.getEntityName(), attributeName, formula);

		if (logicGroup.derivations.containsKey(attributeName)) {
			AbstractRule rule = logicGroup.derivations.get(attributeName);
			throw new RuntimeException("Formula " + logicGroup.getLogicClassName() + "." + methodAnalysis.getMethodName() +
					" is supposed to derive the value of attribute " + attributeName + ", but that attribute's value " +
					"is also derived by " + rule.getLogicMethodName() + ". An attribute can be derived only by one method.");
		}
		logicGroup.derivations.put(attributeName, formula);
		
		// Remove self-dependency: a formula is allowed to refer to its attribute
		MetaModel mmodel = logicGroup.getMetaEntity().getMetaModel();
		String entityName = logicGroup.getMetaEntity().getEntityName();
		ClassDependency classDepend = LogicAnalysisManager.getInstance(mmodel).getDependencyForClass(entityName);
		List<PropertyDependency> selfDeps = methodAnalysis.getDependencies().get(classDepend);
		PropertyDependency selfPropDep = classDepend.getOrCreatePropertyDependency(attributeName, null);
		if (selfPropDep != null && selfDeps != null) {
			selfDeps.remove(selfPropDep);
		}

		// Remember the dependencies for later, when we figure them all out
		if (expression == null || expression.trim().length() == 0) {
			Set<PropertyDependency> propDepends = new HashSet<PropertyDependency>();
			for (List<PropertyDependency> deps : methodAnalysis.getDependencies().values()) {
				propDepends.addAll(deps);
				for (PropertyDependency propDep : deps) {
					RuleDependency ruleDep = new RuleDependency(propDep.getClassDependency().getClassName(),
							propDep.getPropertyName(), propDep.getRoleName());
					formula.addDependency(ruleDep);
				}
			}
			logicGroup.dependencies.put(formula, propDepends);
		}
		else {
			Map<ClassDependency, List<PropertyDependency>> depends = 
					JexlExpressionAnalyzer.getDependenciesFromExpression(formula.getExpression(), 
							logicGroup.metaEntity, methodAnalysis.getMethodName());
			
			// Remove dependency on formula's own attribute, if present
			List<PropertyDependency> thisClassDepends = depends.get(classDepend);
			if (thisClassDepends != null) {
				thisClassDepends.remove(selfPropDep);
			}
			
			Set<PropertyDependency> propDepends = new HashSet<PropertyDependency>();
			for (List<PropertyDependency> deps : depends.values()) {
				propDepends.addAll(deps);
				for (PropertyDependency propDep : deps) {
					RuleDependency ruleDep = new RuleDependency(propDep.getClassDependency().getClassName(),
							propDep.getPropertyName(), propDep.getRoleName());
					formula.addDependency(ruleDep);
				}
			}
			logicGroup.dependencies.put(formula, propDepends);
		}
		logicGroup.methodDerivationMap.put(methodAnalysis.getMethodName(), attributeName);

		return formula;
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
 