package com.autobizlogic.abl.logic.analysis;

import groovy.lang.GroovyObject;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import com.autobizlogic.abl.metadata.MetaEntity;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * Analyze a Java class' byte code to determine its dependencies. This will look at the byte code for the given class,
 * analyze each method, and determine what calls to other classes of the same package it makes.
 */
public class LogicClassAnalysis extends LogicAnalysis
{	
	/**
	 * The full name of the logic class
	 */
	protected String logicClassName;
	
	/**
	 * The CtClass for the logic class
	 */
	protected CtClass ctClass;
	
	/**
	 * This gets set to true once the analysis has been performed so it does not get done twice.
	 */
	protected boolean analysisPerformed;
	
	/**
	 * The metadata for our entity
	 */
	protected MetaEntity metaEntity;
	
	
	/**
	 * LogicClass is Groovy.  Perhaps better to define Enum for later expansion?
	 */
	protected boolean isGroovy = false;
	
	/**
	 * If the has any annotations, they will be stored here.
	 */
	protected Map<String, AnnotationEntry> classAnnotations;
	
	/**
	 * The analysis of the methods for this class.
	 */
	protected Set<LogicMethodAnalysis> methodAnalyses;
	
	/**
	 * The analysis of the relevant fields for this class
	 */
	protected Set<LogicFieldAnalysis> fieldAnalyses = new HashSet<LogicFieldAnalysis>();
	
	/**
	 * We need the name of the CurrentBean variable (if any) for method bytecode analysis, so we
	 * store it here.
	 */
	protected String currentBeanName;
	
	/**
	 * We need the name of the OldBean variable (if any) for method bytecode analysis, so we
	 * store it here.
	 */
	protected String oldBeanName;
	
	/**
	 * The constructor is protected because instances should be retrieved from LogicAnalysisManager.
	 * @param logicClassName The name of the class containing the business logic.
	 */
	protected LogicClassAnalysis(String logicClassName, MetaEntity metaEntity)
	{
		this.logicClassName = logicClassName;
		this.metaEntity = metaEntity;
	}
	
	/**
	 * Get the full name of the logic class being analyzed
	 * @return
	 */
	public String getLogicClassName()
	{
		return logicClassName;
	}
	
	/**
	 * Get the meta entity for this logic class.
	 */
	public MetaEntity getMetaEntity() {
		return metaEntity;
	}
	
	/**
	 * Get the name (if any) of the variable that holds the current bean.
	 */
	public String getCurrentBeanName() {
		analyzeClass();
		return currentBeanName;
	}
	
	/**
	 * Get the name (if any) of the variable that holds the old bean.
	 */
	public String getOldBeanName() {
		analyzeClass();
		return oldBeanName;
	}
	
	/**
	 * 
	 * @return true if LogicClass is Groovy
	 */
	public boolean isGroovy() {
		return isGroovy;
	}
	
	/**
	 * Get the annotation with the given name, if it exists. If it does not, null is returned.
	 */
	public AnnotationEntry getClassAnnotation(String annotationName) {
		
		analyzeClass();
		if (classAnnotations == null)
			return null;
		
		return classAnnotations.get(annotationName);
	}
	
	/**
	 * Get an analysis of all the business logic methods in the business logic class
	 * @return Can be empty if the class has no business logic.
	 */
	public Set<LogicMethodAnalysis> getMethodAnalyses()
	{
		analyzeClass();

		return new CopyOnWriteArraySet<LogicMethodAnalysis>(methodAnalyses);
	}
	
	/**
	 * Get the analysis for a given method. Note that if the method has more than
	 * one implementation (i.e. it is overloaded), this will throw an exception.
	 */
	public LogicMethodAnalysis getMethodAnalysis(String methodName) {
		analyzeClass();
		
		LogicMethodAnalysis methAnal = null;
		for (LogicMethodAnalysis ma : methodAnalyses) {
			if (ma.getMethodName().equals(methodName)) {
				if (methAnal != null)
					throw new RuntimeException("Logic method is overloaded (this is not allowed): " +
							logicClassName + "." + methodName);
				methAnal = ma;
			}
		}
		
		return methAnal;
	}
	
	/**
	 * Get a method analysis, assuming that the analysis has already been done.
	 */
	private LogicMethodAnalysis retrieveMethodAnalysis(String methodName) {
		for (LogicMethodAnalysis ma : methodAnalyses) {
			if (ma.getMethodName().equals(methodName)) {
				return ma;
			}
		}
		
		return null;
	}
	
	public Set<LogicFieldAnalysis> getFieldAnalyses() {
		analyzeClass();
		return fieldAnalyses;
	}
	
	/**
	 * Actually analyze the class 
	 */
	protected void analyzeClass()
	{
		if (analysisPerformed)
			return;
		
		Class<?> logicCls = ClassLoaderManager.getInstance().getLogicClassFromName(getLogicClassName());
		if (GroovyObject.class.isAssignableFrom(logicCls)) {
			if (log.isDebugEnabled()) log.debug("Analyzing dependencies for Groovy logic class : " + logicClassName);
			isGroovy = true;
		} else {
			if (log.isDebugEnabled()) log.debug("Analyzing dependencies for Java logic class : " + logicClassName);
			
		}

		// First check whether the class has any of our annotations
		Object[] annotations = null;
		try {
			annotations = getClassInfo().getAnnotations();
		}
		catch (ClassNotFoundException ex) {
			throw new DependencyException("Annotation class not found : " + ex.getLocalizedMessage(), ex);
		}
		
		classAnnotations = readAnnotations(annotations, "Logic class " + this.logicClassName);
		
		// Also retrieve the relevant variables (i.e. those with our annotations)
		CtClass cls = getClassInfo();
		String realClassName = cls.getName();
		while ( ! cls.getName().equals("java.lang.Object")) {
			CtField[] fields = cls.getDeclaredFields();
			for (CtField field : fields) {
				// Ignore private fields in superclasses
				if ((field.getModifiers() & Modifier.PRIVATE) != 0 && !cls.getName().equals(realClassName))
					continue;
				try {
					annotations = field.getAnnotations();
				} catch (ClassNotFoundException ex) {
					throw new RuntimeException("Class not found for annotation - logic class " + logicClassName +
							", field " + field.getName(), ex);
				}
				Map<String, AnnotationEntry> annots = readAnnotations(annotations, "Logic class " + logicClassName + 
						", field " + field.getName());
				
				// If the field has at least one of our annotations, make note of it
				if (annots.size() > 0) {
					String fldClassName = null;
					try {
						fldClassName = field.getType().getName();
					} catch(NotFoundException ex) {
						throw new RuntimeException("Type not found for logic class " + logicClassName +
								", field " + field.getName(), ex);
					}
					LogicFieldAnalysis fldAnalysis = null;
					try {
						fldAnalysis = new LogicFieldAnalysis(field.getName(), fldClassName, field.getType(), annots);
					}
					catch(NotFoundException ex) {
						throw new RuntimeException("Unable to find type for field " + logicClassName + "." + field.getName(), ex);
					}
					fieldAnalyses.add(fldAnalysis);
					
					if (fldAnalysis.getAnnotations().containsKey("CurrentBean")) {
						if (currentBeanName == null) {
							currentBeanName = fldAnalysis.getFieldName();
							if (getMetaEntity().isMap() && (! fldClassName.equals("java.util.Map")))
								throw new RuntimeException("CurrentBean field must be a Map<String, Object> for " +
										logicClassName);
						}
						else // CurrentBean is already set: this must be an overridden inherited field -- ignore
							fieldAnalyses.remove(fldAnalysis);
					}
					else if (fldAnalysis.getAnnotations().containsKey("OldBean")) {
						if (oldBeanName == null) {
							oldBeanName = fldAnalysis.getFieldName();
							if (getMetaEntity().isMap() && (! fldClassName.equals("java.util.Map")))
								throw new RuntimeException("OldBean field must be a Map<String, Object> for " +
										logicClassName);
						}
						else // OldBean is already set: this must be an overridden inherited field -- ignore
							fieldAnalyses.remove(fldAnalysis);
					}
				}
			}
			try {
				cls = cls.getSuperclass();
			}
			catch(Exception ex) {
				throw new RuntimeException("Exception while analyzing fields for " + 
						getClassInfo().getName() + " while getting superclass of " + cls.getName(), ex);
			}
		}

		methodAnalyses = new HashSet<LogicMethodAnalysis>();
		cls = getClassInfo();
		while ( ! cls.getName().equals("java.lang.Object")) {
			CtMethod[] methods = cls.getDeclaredMethods();
			for (CtMethod method: methods) {
				// Ignore private methods in superclasses
				if ((method.getModifiers() & Modifier.PRIVATE) != 0 && !cls.getName().equals(realClassName))
					continue;
				LogicMethodAnalysis methodAnalysis = new LogicMethodDeepAnalysis(this, method);
				if (methodAnalysis.hasBusinessLogic()) { // This will cause the method to be analyzed
					// If it's already there, it's overridden
					boolean alreadyThere = false;
					for (LogicMethodAnalysis lma : methodAnalyses) {
						if (methodAnalysis.getMethodName().equals(lma.getMethodName())) {
							alreadyThere = true;
							break;
						}
					}
					if ( ! alreadyThere)
						methodAnalyses.add(methodAnalysis);
				}
			}
			try {
				cls = cls.getSuperclass();
			}
			catch(Exception ex) {
				throw new RuntimeException("Exception while analyzing methods for " + 
						getClassInfo().getName() + " while getting superclass of " + cls.getName(), ex);
			}
		}
		
		// Now look at each method and convert any method dependencies into property dependencies.
		for (LogicMethodAnalysis mAnal : methodAnalyses) {
			Set<MethodDependency> methDepends = mAnal.getMethodDependencies();
			for (MethodDependency methDepend : methDepends) {
				LogicMethodAnalysis calledMeth = this.retrieveMethodAnalysis(methDepend.getMethodName());
				if (calledMeth == null)
					throw new RuntimeException("Internal error: method dependency to non-existent method, " +
							"in class " + this.getLogicClassName() + ", from method " + mAnal.getMethodName() +
							" to method " + methDepend.getMethodName());
				
				// Now add all the called method's dependencies to this method
				Map<ClassDependency, List<PropertyDependency>> transDeps = calledMeth.getDependencies();
				for (ClassDependency classDep : transDeps.keySet()) {
					List<PropertyDependency> transPropDeps = transDeps.get(classDep);
					for (PropertyDependency transPropDep : transPropDeps) {
						mAnal.addPropertyDependency(classDep.getClassName(), 
								transPropDep.getPropertyName(), transPropDep.getRoleName());
					}
				}
			}
		}
		
		analysisPerformed = true;
	}
	
	/**
	 * Get the CtClass for this class.
	 * @return The CtClass for this class.
	 */
	protected CtClass getClassInfo()
	{
		if (ctClass != null)
			return ctClass;
		
		ctClass = ClassLoaderManager.getInstance().getClassInfo(logicClassName);
		return ctClass;
	}
	
	///////////////////////////////////////////////////////////////////////////////
	// Menial stuff
	
	@Override
	public String toString() {
		return "LogicClassAnalysis for " + this.logicClassName;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicClassAnalysis.java 1157 2012-04-11 10:26:31Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 