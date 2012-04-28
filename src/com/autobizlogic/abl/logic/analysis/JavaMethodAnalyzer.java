package com.autobizlogic.abl.logic.analysis;

import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.BeanNameUtil;

import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * Given a method, figure out the references it makes to the class' attributes,
 * and to any direct parent's attributes.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class JavaMethodAnalyzer extends MethodAnalyzer {
	
	/**
	 * Do not use this directly. This is an internal class.
	 */
	protected JavaMethodAnalyzer(LogicMethodAnalysis methodAnalysis, CtMethod method) {
		super(methodAnalysis, method);
	}
	
	/**
	 * Declare the patterns of bytecode that we're looking for, and their handlers.
	 * Note that order of the patterns is critical: more specific patterns should come
	 * before more general patterns.
	 */
	protected void analyzeJavaMethod() {
		
		// Keep track of entity local variables. Key is the variable name, the value is
		// [0] entity name, [1] role used to access it
		final Map<String, String[]> localJavaVars = new HashMap<String, String[]>();
		
		final String currentBeanName = methodAnalysis.classAnalysis.currentBeanName;
		
		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for:
		// customer.getBalance();
		Pattern pattern1 = new Pattern(this);
		pattern1.opcodePattern = new OpType[]{OpType.ALOAD, OpType.GETFIELD, OpType.INVOKE};
		pattern1.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload this
				if ( ! "this".equals(analyzer.allCode.get(idx).info[0])) return false;
				// getfield <currentBean>
				if ( ! currentBeanName.equals(analyzer.allCode.get(idx + 1).info[1])) return false;
				// invokevirtual com.foo.Bean.method
				String methodName = analyzer.allCode.get(idx + 2).info[1];
				
				String attName = BeanNameUtil.getPropNameFromGetMethodName(methodName);
				if (attName == null)
					return false;
				MetaAttribute metaAttrib = analyzer.metaEntity.getMetaAttribute(attName);
				if (metaAttrib == null)
					return false;
				
				// We have a bona fide access to an attribute
				analyzer.methodAnalysis.addPropertyDependency(analyzer.metaEntity.getEntityName(), attName, null);

				return true;
			}
		};
		addPattern(pattern1);

		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for:
		// customer.getOrganization().getName();
		Pattern pattern2 = new Pattern(this);
		pattern2.opcodePattern = new OpType[]{OpType.ALOAD, OpType.GETFIELD, OpType.INVOKE, OpType.INVOKE};
		pattern2.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload this
				if ( ! "this".equals(analyzer.allCode.get(idx).info[0])) return false;
				// getfield <currentBean>
				if ( ! currentBeanName.equals(analyzer.allCode.get(idx + 1).info[1])) return false;
				// invokevirtual com.foo.Bean.method
				String methodName = analyzer.allCode.get(idx + 2).info[1];
				String propName = BeanNameUtil.getPropNameFromGetMethodName(methodName);
				if (propName == null)
					return false;
				MetaRole metaRole = analyzer.metaEntity.getMetaRole(propName);
				if (metaRole == null)
					return false;
				if (metaRole.isCollection())
					return false;
				// invokevirtual com.foo.Bean.method
				String methodName2 = analyzer.allCode.get(idx + 3).info[1];
				String attName = BeanNameUtil.getPropNameFromGetMethodName(methodName2);
				if (attName == null)
					return false;
				MetaAttribute metaAtt = metaRole.getOtherMetaEntity().getMetaAttribute(attName);
				if (metaAtt == null)
					return false;
				
				// We have a bona fide access to an attribute
				analyzer.methodAnalysis.addPropertyDependency(metaRole.getOtherMetaEntity().getEntityName(), attName, propName);

				return true;
			}
		};
		addPattern(pattern2);
		
		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for:
		// Organization org = customer.getOrganization();
		// so we can remember that it's in a local variable.
		Pattern pattern3 = new Pattern(this);
		pattern3.opcodePattern = new OpType[]{OpType.ALOAD, OpType.GETFIELD, OpType.INVOKE, OpType.ASTORE};
		pattern3.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload this
				if ( ! "this".equals(analyzer.allCode.get(idx).info[0])) return false;
				// getfield <currentBean>
				if ( ! currentBeanName.equals(analyzer.allCode.get(idx + 1).info[1])) return false;
				// invokevirtual com.foo.Bean.method
				String methodName = analyzer.allCode.get(idx + 2).info[1];
				String propName = BeanNameUtil.getPropNameFromGetMethodName(methodName);
				if (propName == null)
					return false;
				MetaRole metaRole = analyzer.metaEntity.getMetaRole(propName);
				if (metaRole == null)
					return false;
				if (metaRole.isCollection())
					return false;
				// astore <var-name>
				String varName = analyzer.allCode.get(idx + 3).info[0];
				
				// It's a local variable
				localJavaVars.put(varName, new String[]{metaRole.getOtherMetaEntity().getEntityName(), propName});

				return true;
			}
		};
		addPattern(pattern3);

		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for:
		// org.getName(); // Where org is a local variable set to an entity
		Pattern pattern4 = new Pattern(this);
		pattern4.opcodePattern = new OpType[]{OpType.ALOAD, OpType.INVOKE};
		pattern4.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload <var-name>
				if (analyzer.allCode.get(idx).info == null) // This can happen in e.g. loops
					return false;
				String varName = analyzer.allCode.get(idx).info[0];
				if ( ! localJavaVars.containsKey(varName)) return false;
				// invokevirtual com.foo.Bean.method
				String methodName = analyzer.allCode.get(idx + 1).info[1];
				String attName = BeanNameUtil.getPropNameFromGetMethodName(methodName);
				if (attName == null) return false;

				String[] varDetails = localJavaVars.get(varName);
				String varClassName = varDetails[0];
				String varRole = varDetails[1];
				MetaEntity varEntity = analyzer.metaEntity.getMetaModel().getMetaEntity(varClassName);
				if (varEntity == null) return false;
				MetaAttribute varAttrib = varEntity.getMetaAttribute(attName);
				if (varAttrib == null) return false;
				
				// We have a bona fide access to an attribute
				analyzer.methodAnalysis.addPropertyDependency(varEntity.getEntityName(), attName, varRole);

				return true;
			}
		};
		addPattern(pattern4);

		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for:
		// deriveAmount(); // If it's a method for a derived attribute, note the dependency
		Pattern pattern5 = new Pattern(this);
		pattern5.opcodePattern = new OpType[]{OpType.ALOAD, OpType.INVOKE};
		pattern5.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload <var-name>
				if (analyzer.allCode.get(idx).info == null) // This can happen in e.g. loops
					return false;
				String varName = analyzer.allCode.get(idx).info[0];
				if ( ! "this".equals(varName)) return false;
				// invokevirtual com.foo.Bean.method
				String methodName = analyzer.allCode.get(idx + 1).info[1];
				
				// Is it a formula?
				CtMethod ctMethod = null;
				try {
					ctMethod = analyzer.method.getDeclaringClass().getDeclaredMethod(methodName);
				}
				catch(NotFoundException ex) {
					return false;
				}
				Map<String, AnnotationEntry> allAnnots = LogicMethodAnalysis.readMethodAnnotations(ctMethod);
				if (allAnnots.containsKey("Formula")) {
					analyzer.methodAnalysis.addMethodDependency(methodName);
					return true;
				}

				return false;
			}
		};
		addPattern(pattern5);
		
		analyzeMethod();
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
 