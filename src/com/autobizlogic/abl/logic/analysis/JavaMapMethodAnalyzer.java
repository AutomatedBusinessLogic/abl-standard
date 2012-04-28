package com.autobizlogic.abl.logic.analysis;

import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;

import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * Given a method, figure out the references it makes to the class' attributes,
 * and to any direct parent's attributes.
 * This version is for MAP beans, as opposed to POJO beans.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class JavaMapMethodAnalyzer extends MethodAnalyzer {

	protected JavaMapMethodAnalyzer(LogicMethodAnalysis methodAnalysis, CtMethod method) {
		super(methodAnalysis, method);
	}

	protected void analyzeJavaMethod() {
		
		// Keep track of entity local variables. Key is the variable name, the value is
		// [0] entity name, [1] role used to access it
		final Map<String, String[]> localJavaVars = new HashMap<String, String[]>();
		
		final String currentBeanName = methodAnalysis.classAnalysis.currentBeanName;
		
		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for (assuming customer is a Map and organization is a parent):
		// Map<String, Object> org = (Map<String, Object>)customer.get("organization");
		// This allows us to keep track of what entities are loaded in which local
		// variables.
		Pattern pattern1 = new Pattern(this);
		pattern1.opcodePattern = new OpType[]{OpType.ALOAD, OpType.GETFIELD, OpType.LDC, OpType.INVOKE,
				OpType.CHECKCAST, OpType.ASTORE};
		pattern1.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload this
				if ( ! "this".equals(analyzer.allCode.get(idx).info[0])) return false;
				// getfield <currentBean>
				if ( ! currentBeanName.equals(analyzer.allCode.get(idx + 1).info[1])) return false;
				// ldc <property name>
				String propName = analyzer.allCode.get(idx + 2).info[0];
				// invokeinterface java.util.Map.get
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 3).info[0])) return false;
				if ( ! "get".equals(analyzer.allCode.get(idx + 3).info[1])) return false;
				// checkcast java.util.Map
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 4).info[0])) return false;
				// astore <variable name>
				String varName = analyzer.allCode.get(idx + 5).info[0];
				
				// Is the property being accessed one that is a parent?
				MetaRole role = analyzer.metaEntity.getMetaRole(propName);
				if (role == null) // Whatever it was, it wasn't a relationship
					return false;
				
				// If we're not the child of this relationship, it doesn't count
				if (role.isCollection())
					return false;
				
				localJavaVars.put(varName, new String[]{role.getOtherMetaEntity().getEntityName(), propName});
				
				return true;
			}
		};
		addPattern(pattern1);
		
		//////////////////////////////////////////////////////////////////////
		// Byte code pattern for:
		// org.get("name"); // org being a local variable
		Pattern pattern2 = new Pattern(this);
		pattern2.opcodePattern = new OpType[]{OpType.ALOAD, OpType.LDC, OpType.INVOKE};
		pattern2.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload <local variable>
				String varName = analyzer.allCode.get(idx).info[0];
				if ( ! localJavaVars.containsKey(varName)) return false;
				// ldc <property name>
				String propName = analyzer.allCode.get(idx + 1).info[0];
				// invokeinterface java.util.Map.get
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 2).info[0])) return false;
				if ( ! "get".equals(analyzer.allCode.get(idx + 2).info[1])) return false;
				
				// Is the property being accessed an attribute of the parent?
				String[] varEntry = localJavaVars.get(varName);
				String entityName = varEntry[0];
				String roleName = varEntry[1];
				MetaEntity varEntity = analyzer.metaEntity.getMetaModel().getMetaEntity(varEntry[0]);
				if (varEntity == null) return false;
				MetaAttribute att = varEntity.getMetaAttribute(propName);
				if (att == null) // Whatever it was, it wasn't an attribute
					return false;

				// So now it looks like we have a bona fide access to a parent attribute
				analyzer.methodAnalysis.addPropertyDependency(entityName, propName, roleName);
				
				return true;
			}
		};
		addPattern(pattern2);
		
		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for (assuming customer is a Map and balance is an attribute):
		// customer.get("balance");
		Pattern pattern3 = new Pattern(this);
		pattern3.opcodePattern = new OpType[]{OpType.ALOAD, OpType.GETFIELD, OpType.LDC, OpType.INVOKE};
		pattern3.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload this
				if ( ! "this".equals(analyzer.allCode.get(idx).info[0])) return false;
				// getfield <currentBean>
				if ( ! currentBeanName.equals(analyzer.allCode.get(idx + 1).info[1])) return false;
				// ldc <property name>
				String propName = analyzer.allCode.get(idx + 2).info[0];
				// invokeinterface java.util.Map.get
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 3).info[0])) return false;
				if ( ! "get".equals(analyzer.allCode.get(idx + 3).info[1])) return false;
				
				// Is the attribute being accessed persistent?
				MetaAttribute att = analyzer.metaEntity.getMetaAttribute(propName);
				if (att == null) return false;
				
				// So now it looks like we have a bona fide access to an attribute
				analyzer.methodAnalysis.addPropertyDependency(analyzer.metaEntity.getEntityName(), propName, null);

				return true;
			}
		};
		addPattern(pattern3);
		
		///////////////////////////
//		15  aload_0 [this]
//		16  getfield abl.test.businesslogic.map.more.CustomerLogic.customer : java.util.Map [24]
//		19  ldc <String "organization"> [36]
//		21  invokeinterface java.util.Map.get(java.lang.Object) : java.lang.Object [28] [nargs: 2]
//		26  checkcast java.util.Map [29]
//		29  ldc <String "name"> [38]
//		31  invokeinterface java.util.Map.get(java.lang.Object) : java.lang.Object [28] [nargs: 2]
		///////////////////////////////////////////////////////////////////////////
		// Byte code pattern for (assuming customer is the CurrentBean map):
		// String orgName = (String)((Map<String, Object>)customer.get("organization")).get("name");
		Pattern pattern4 = new Pattern(this);
		pattern4.opcodePattern = new OpType[]{OpType.ALOAD, OpType.GETFIELD, OpType.LDC, OpType.INVOKE,
				OpType.CHECKCAST, OpType.LDC, OpType.INVOKE};
		pattern4.action = new PatternAction(){
			@Override
			public boolean patternFound(MethodAnalyzer analyzer, int idx) {
				// aload this
				if ( ! "this".equals(analyzer.allCode.get(idx).info[0])) return false;
				// getfield <currentBean>
				if ( ! currentBeanName.equals(analyzer.allCode.get(idx + 1).info[1])) return false;
				// ldc <property name>
				String propName = analyzer.allCode.get(idx + 2).info[0];
				// invokeinterface java.util.Map.get
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 3).info[0])) return false;
				if ( ! "get".equals(analyzer.allCode.get(idx + 3).info[1])) return false;
				// checkcast java.util.Map
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 4).info[0])) return false;
				// ldc <attribute name>
				String attName = analyzer.allCode.get(idx + 5).info[0];
				// invokeinterface java.util.Map.get
				if ( ! "java.util.Map".equals(analyzer.allCode.get(idx + 6).info[0])) return false;
				if ( ! "get".equals(analyzer.allCode.get(idx + 6).info[1])) return false;

				// Is the property being accessed one that is a parent?
				MetaRole role = analyzer.metaEntity.getMetaRole(propName);
				if (role == null) // Whatever it was, it wasn't a relationship
					return false;
				
				// If we're not the child of this relationship, it doesn't count
				if (role.isCollection())
					return false;

				// Is the attribute being accessed persistent?
				MetaAttribute att = role.getOtherMetaEntity().getMetaAttribute(attName);
				if (att == null) return false;
				
				// We have a bona fide dependency
				analyzer.methodAnalysis.addPropertyDependency(role.getOtherMetaEntity().getEntityName(), attName, propName);

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
 