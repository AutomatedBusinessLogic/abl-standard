package com.autobizlogic.abl.logic.analysis;

import java.util.Set;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.MethodInfo;

/**
 * Extension of LogicMethodAnalysis that also does bytecode analysis to figure out
 * the dependencies.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class LogicMethodDeepAnalysis extends LogicMethodAnalysis {

	protected LogicMethodDeepAnalysis(LogicClassAnalysis classAnalysis, CtMethod method) {
		super(classAnalysis, method);
	}
	
	@Override
	protected void analyze() {
		if (analysisPerformed)
			return;
		
		// Start with the standard analysis
		super.analyze();
		
		// None of the annotations were ours -- this method is not relevant
		if ( ! hasBusinessLogic)
			return;

		// Determine the return type
		try {
			CtClass returnClass = method.getReturnType();
			if (returnClass != null)
				returnTypeName = returnClass.getName();
		} catch (NotFoundException ex) {
			throw new DependencyException("Error while analyzing business logic class " + classAnalysis.getLogicClassName() +
					", unable to determine return type for method " + getMethodName(), ex);
		}		

		// Now go through the method's code and see what methods it calls
		MethodInfo info = method.getMethodInfo2();
        CodeAttribute code = info.getCodeAttribute();

        // We keep track of the code size as an indication for possible optimization,
        // in particular when the method is empty (as in the case of sum and count, typically).
        codeSize = 0;
        if (code != null)
        	codeSize = code.getCodeLength();
        
        if (code == null || codeSize <= 1) // If there is no code, or just empty code
            return;
		
        // We really don't care about the code for these since it's executed purely for debugging.
		if (type == Type.COUNT || type == Type.PARENTCOPY || type == Type.SUM)
			return;
		
		// If there is no @CurrentBean or @OldBean variable, the code evidently does not rely on the bean.
		if (classAnalysis.currentBeanName == null)
			return;

		if (this.getClassAnalysis().getMetaEntity().isPojo()) {
			if (GroovyMethodAnalyzer.classIsGroovy(method.getDeclaringClass().getName())) {
				Set<String[]> deps = 
						GroovyMethodAnalyzer.analyzeGroovyMethodDependencies(method, 
								classAnalysis.getMetaEntity(), classAnalysis.currentBeanName);
				for (String[] dep : deps) {
					addPropertyDependency(dep[0], dep[1], dep[2]);
				}
			}
			else {
				JavaMethodAnalyzer javaAnalyzer = new JavaMethodAnalyzer(this, method);
				javaAnalyzer.analyzeJavaMethod();
			}
		}
		else if (this.getClassAnalysis().getMetaEntity().isMap()) {
			if (GroovyMethodAnalyzer.classIsGroovy(method.getDeclaringClass().getName())) {
				Set<String[]> deps = 
						GroovyMethodAnalyzer.analyzeGroovyMethodDependencies(method, 
								classAnalysis.getMetaEntity(), classAnalysis.currentBeanName);
				for (String[] dep : deps) {
					addPropertyDependency(dep[0], dep[1], dep[2]);
				}
			}
			else {
				JavaMapMethodAnalyzer javaAnalyzer = new JavaMapMethodAnalyzer(this, method);
				javaAnalyzer.analyzeJavaMethod();
			}
		}
		else {
			throw new RuntimeException("Entity is neither Pojo nor Map: " + 
					getClassAnalysis().getMetaEntity());
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicMethodDeepAnalysis.java 1231 2012-04-21 10:28:06Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 