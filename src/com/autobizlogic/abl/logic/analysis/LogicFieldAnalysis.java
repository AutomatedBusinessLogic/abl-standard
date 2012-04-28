package com.autobizlogic.abl.logic.analysis;

import java.util.Map;

import javassist.CtClass;

/**
 * The result of analyzing one field in a logic class.
 */
public class LogicFieldAnalysis extends LogicAnalysis {
	
	private String fieldName;
	
	private String fieldClassName;
	
	private CtClass fieldCtClass;
	
	private Map<String, AnnotationEntry> annotations;
	
	protected LogicFieldAnalysis(String fieldName, String fieldClassName, CtClass fieldCtClass, 
			Map<String, AnnotationEntry> annotations) {
		this.fieldName = fieldName;
		this.fieldCtClass = fieldCtClass;
		this.fieldClassName = fieldClassName;
		this.annotations = annotations;
	}
	
	public String getFieldName() {
		return fieldName;
	}
	
	public String getFieldClassName() {
		return fieldClassName;
	}
	
	public CtClass getFieldCtClass() {
		return fieldCtClass;
	}
	
	public Map<String, AnnotationEntry> getAnnotations() {
		return annotations;
	}
	
	@Override
	public String toString() {
		return "Field analysis for " + fieldName;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicFieldAnalysis.java 1056 2012-04-03 21:40:50Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 