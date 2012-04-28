package com.autobizlogic.abl.logic.analysis;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import javassist.CtMethod;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

import org.codehaus.groovy.runtime.callsite.CallSite;

import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.BeanNameUtil;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.NodalPathUtil;

//import quicktests.groovy.SimpleBean;

import groovy.lang.GroovyObject;

/**
 * This class is very tricky and requires a good understanding of Java bytecode AND of Groovy
 * internals. If you don't have both, you're in for a world of pain.
 * <p/>
 * We look for some very specific patterns in the bytecode, which represent calls to either method
 * or properties. This is made much more difficult by Groovy because all calls are "virtual", i.e.
 * all method calls are actually calls to CallSite.call and all property accesses are actually calls
 * to CallSite.callGetProperty.
 * <p/>
 * Therefore we have to get the array of CallSite for the object, which thankfully we don't read
 * directly from the bytecode, but rather get by creating an instance of the Groovy class, and calling
 * its (completely undocumented) $getCallSiteArray method.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class GroovyMethodAnalyzer {

	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.DEPENDENCY);
	
	protected static boolean classIsGroovy(String className) {
		Class<?> cls = ClassLoaderManager.getInstance().getLogicClassFromName(className);
		return GroovyObject.class.isAssignableFrom(cls);
	}
	
	/**
	 * To analyze the method, we create an instance of the bean so we can poke it using the
	 * GroovyObject interface, and in particular call the synthetic $getCallSiteArray method.
	 * @param ctMethod The method to be analyzed
	 * @param beanClass The class of the persistent bean
	 * @param beanName The name of the variable that holds the persistent bean
	 */
	protected static Set<String[]> analyzeGroovyMethodDependencies(CtMethod ctMethod, MetaEntity metaEntity, String beanName) {
		Object instance = null;
		Class<?> logicClass = null;
		try {
			String clsName = ctMethod.getDeclaringClass().getName();
			logicClass = ClassLoaderManager.getInstance().getLogicClassFromName(clsName);
			instance = logicClass.newInstance();
		}
		catch(Exception ex) {
			throw new RuntimeException("Unable to analyze Groovy class " + ctMethod.getClass().getName(), ex);
		}
		
		GroovyObject gobject = (GroovyObject)instance;
		CallSite[] callSites = (CallSite[])gobject.invokeMethod("$getCallSiteArray", null);
		
		MethodInfo info = ctMethod.getMethodInfo2();
		ConstPool pool = info.getConstPool();
		
        CodeAttribute code = info.getCodeAttribute();
        CodeIterator iterator = code.iterator();
        
        // Keep track of the latest object pushed onto the stack
        String classOnTopOfTheStack = null; // The class of the object on top of the stack
        String roleOnTopOfTheStack = null; // The role (if any) used to retrieve the object on top of the stack
        
        // Keep track of where the following patterns occur:
		// ldc	#89; OR ldc_w #257;
		// aaload
		// aload_0 OR aload_1 OR aload_2 OR aload_3
        // Whenever such a pattern occurs, we use the code index as the key, and the value being pushed on
        // the stack by the first instruction as the value
        Stack<Integer> arrayIndices = new Stack<Integer>();
        
        // Keep track of what methods are called. [0] = class, [1] = method, [2] = role used to get object
        Set<String[]> dependencies = new HashSet<String[]>();
        
        while (iterator.hasNext()) {
        	int pos;
        	try {
        		pos = iterator.next();
        	} catch (BadBytecode ex) {
        		throw new DependencyException("Bad byte code in class " + 
        				ctMethod.getDeclaringClass().getName() + ", method " + ctMethod.getLongName(), ex);
        	}
        	
        	int opcode = iterator.byteAt(pos);
        	
        	// If this is not a method call, forget about the object that was pushed on the stack (if any)
        	if (opcode != Opcode.INVOKEINTERFACE) {
        		classOnTopOfTheStack = null;
        		roleOnTopOfTheStack = null;
        	}
        	
        	switch (opcode) {
	        	case Opcode.GETFIELD : {
	        		
	        		String fieldName = pool.getFieldrefName(iterator.u16bitAt(pos + 1));
	        		
	        		if ( ! beanName.equals(fieldName))
	        			continue;
	        		
	        		Field field;
	        		try {
	        			field = logicClass.getDeclaredField(fieldName);
	        		}
	        		catch(NoSuchFieldException ex) {
	        			throw new RuntimeException("Unable to find field " + fieldName, ex);
	        		}

	        		if (metaEntity.isMap()) {
		        		classOnTopOfTheStack = metaEntity.getEntityName();
		        		roleOnTopOfTheStack = null;
	        		}
	        		else {
		        		// We're only interested in access to a field if its class belongs to the same package
		        		// as the bean
	        			String fieldTypeName = field.getType().getName();
	        			String fieldTypePackageName = NodalPathUtil.getNodalPathPrefix(fieldTypeName);
	        			String entityClassName = metaEntity.getEntityClass().getName();
	        			String entityPackageName = NodalPathUtil.getNodalPathPrefix(entityClassName);
		        		if ( ! fieldTypePackageName.equals(entityPackageName))
		        			continue;
		        		classOnTopOfTheStack = field.getType().getName();
		        		roleOnTopOfTheStack = null;
	        		}
	        		break;
	        	}
	        	
	        	case Opcode.LDC : {
	        		// We're only interested in the array pattern -- we ignore anything else
	        		int idx = iterator.byteAt(pos + 1);
	        		Object ldcObj = pool.getLdcValue(idx);
	        		if ( ! (ldcObj instanceof Integer))
	        			continue;
	        		int opcode2 = iterator.byteAt(pos + 2);
	        		if (opcode2 != Opcode.AALOAD)
	        			continue;
	        		int opcode3 = iterator.byteAt(pos + 3);
	        		if (opcode3 != Opcode.ALOAD_0 && opcode3 != Opcode.ALOAD_1 && opcode3 != Opcode.ALOAD_2 && opcode3 != Opcode.ALOAD_3)
	        			continue;
	        		arrayIndices.push((Integer)ldcObj);
	        		break;
	        	}
	        	
	        	case Opcode.LDC_W : {
	        		int idx = iterator.u16bitAt(pos + 1);
	        		Object ldcObj = pool.getLdcValue(idx);
	        		if ( ! (ldcObj instanceof Integer))
	        			continue;
	        		int opcode2 = iterator.byteAt(pos + 3);
	        		if (opcode2 != Opcode.AALOAD)
	        			continue;
	        		int opcode3 = iterator.byteAt(pos + 4);
	        		if (opcode3 != Opcode.ALOAD_0 && opcode3 != Opcode.ALOAD_1 && opcode3 != Opcode.ALOAD_2 && opcode3 != Opcode.ALOAD_3)
	        			continue;
	        		arrayIndices.push((Integer)ldcObj);
	        		break;
	        	}
        	
	        	// This never really seems to get called in Groovy
	        	case Opcode.INVOKEVIRTUAL :
	        	case Opcode.INVOKESPECIAL : {
	        		
	        		// This should never happen in practice, but if a method is being called other
	        		// than Groovy's CallSite.call*, then we definitely want to reset the array indices.
	        		arrayIndices.clear();
	        		break;
	        	}

	        	// All call are here because all calls are really calls to CallSite.callXXX
	        	case Opcode.INVOKEINTERFACE : {
	        		int index = iterator.u16bitAt(pos + 1);
	        		String className = pool.getInterfaceMethodrefClassName(index);
	        		String methodName = pool.getInterfaceMethodrefName(index);
	        		
	        		if ( ! className.equals("org.codehaus.groovy.runtime.callsite.CallSite"))
	        			continue;
	        		
	        		if ( ! (methodName.equals("callGetProperty") || methodName.equals("call")))
	        			continue;
	        		
	        		if (classOnTopOfTheStack == null) { // This is not a call to one of our classes
	        			arrayIndices.clear();
	        			continue;
	        		}
	        		
	        		if (arrayIndices.isEmpty())
	        			// 2012/04/23: taking this out since it blows up in a BusLogicIntro test.
//	        			throw new RuntimeException("Unable to find callSite index while analyzing " +
//	        					ctMethod.getLongName() + ", in invocation of " + className + "." + methodName);
	        			continue;
	        		
	        		int callSiteIdx = arrayIndices.pop();
    				CallSite callSite = callSites[callSiteIdx];
    				String callName = callSite.getName();
    				Method method = null;
    				
    				if (metaEntity.isMap()) {
    					MetaAttribute metaAttrib = metaEntity.getMetaAttribute(callName);
    					if (metaAttrib != null) {
	    					String[] dependsEntry = new String[3];
	    					dependsEntry[0] = classOnTopOfTheStack;
	    					dependsEntry[1] = metaAttrib.getName();
	    					dependsEntry[2] = roleOnTopOfTheStack;
	    					dependencies.add(dependsEntry);
	    					
	    					classOnTopOfTheStack = null;
	    					roleOnTopOfTheStack = null;
	    	        		arrayIndices.clear();
    					}
    					else {
    						MetaRole metaRole = metaEntity.getMetaRole(callName);
    						if (metaRole != null) {
    	    					classOnTopOfTheStack = metaRole.getOtherMetaEntity().getEntityName();
    	    					roleOnTopOfTheStack = callName;
    	    	        		//arrayIndices.clear();
    						}
    					}
    					continue;
    				}
    				
    				Class<?> calledClass = null;
    				try {
    					calledClass = ClassLoaderManager.getInstance().getClassFromName(classOnTopOfTheStack);
    				}
    				catch(Exception ex) {
    					throw new RuntimeException("Could not find class: " + classOnTopOfTheStack);
    				}
    				
    				if (methodName.equals("call") && callName.startsWith("get")) {
    					try {
    						method = calledClass.getMethod(callName, (Class<?>[])null);
    					}
    					catch(NoSuchMethodException ex) {
    						// Ignore exception
    					}
    				}
    				else if (methodName.equals("callGetProperty")) { // If it's a property access, translate the name to a getter
    					String fullCallName = "get" + Character.toUpperCase(callName.charAt(0)) + callName.substring(1);
    					try {
    						method = calledClass.getMethod(fullCallName, (Class<?>[])null);
    					}
    					catch(NoSuchMethodException ex) {
    						// Ignore exception
    					}
    				}
    				else
    					continue; // We're not interested in calls to anything but call and callGetProperty
				
    				if (method == null)
    					throw new RuntimeException("Unable to find method " + callName + " for bean " +
    							calledClass.getName() + " while analyzing logic method " + 
    							ctMethod.getDeclaringClass().getName() + "." + ctMethod.getName());
    				
					Class<?> returnType = method.getReturnType();
					if (returnType == null) // We have no interest in methods that return null
						continue;
					
					// If the method returns an object of the package type, we want to continue
					// following it.
					if (metaEntity.getEntityClass().getPackage().equals(returnType.getPackage())) {
						classOnTopOfTheStack = returnType.getName();
						String rolesSoFar = "";
						if (roleOnTopOfTheStack != null)
							rolesSoFar = roleOnTopOfTheStack + ".";
				        if (methodName.equals("call") && callName.startsWith("get")) {
				        	roleOnTopOfTheStack = Character.toLowerCase(callName.charAt(3)) + callName.substring(4);
				        }
				        else if (methodName.equals("callGetProperty")) {
				        	roleOnTopOfTheStack = callName;
				        }
				        else
				        	throw new RuntimeException("Unable to make sense of call to method " + method);
				        roleOnTopOfTheStack = rolesSoFar + roleOnTopOfTheStack;
					}
					else if (Collection.class.isAssignableFrom(returnType)) { // We ignore collections
						log.debug("Ignoring method " + callName + " that returns collection: " + returnType);
					}
					else { // It's a candidate for a dependency
    					String[] dependsEntry = new String[3];
    					dependsEntry[0] = classOnTopOfTheStack;
    					dependsEntry[1] = BeanNameUtil.getPropNameFromGetMethodName(method.getName());
    					dependsEntry[2] = roleOnTopOfTheStack;
    					dependencies.add(dependsEntry);
    					
    					classOnTopOfTheStack = null;
    					roleOnTopOfTheStack = null;
    	        		arrayIndices.clear();
					}
    				

	        		break;
	        	}
        	}
        }
        
        return dependencies;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  GroovyMethodAnalyzer.java 1245 2012-04-23 09:08:18Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 