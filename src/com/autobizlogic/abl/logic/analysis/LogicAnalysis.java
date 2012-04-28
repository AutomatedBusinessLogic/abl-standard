package com.autobizlogic.abl.logic.analysis;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.autobizlogic.abl.annotations.Verbs;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

import javassist.bytecode.annotation.*;

/**
 * Superclass for classes that analyze byte code. Provides some common services.
 */
public class LogicAnalysis {
	
	protected final static LogicLogger log = LogicLogger.getLogger(LoggerName.DEPENDENCY);
	
	private static final String ANNOTATION_PACKAGE = "com.autobizlogic.abl.annotations.";

	/**
	 * Decompose a set of annotations into usable form.
	 * @param annotations The annotation array from Javassist
	 * @param context A string to be used for information if there is an exception
	 * @return A set of annotation entries, keyed by annotation name.
	 */
	public static Map<String, AnnotationEntry> readAnnotations(Object[] annotations, String context) {
		
		Map<String, AnnotationEntry> res = new HashMap<String, AnnotationEntry>();
		
		// Go over the annotations and see if any of them are ours
		for (Object annot: annotations) {
			AnnotationImpl ann = (AnnotationImpl)Proxy.getInvocationHandler(annot); // Don't ask me why you have to do that
			Annotation annotation = ann.getAnnotation();
			String annotationClassName = annotation.getTypeName();
			if ( ! annotationClassName.startsWith(ANNOTATION_PACKAGE))
				continue;
			
			AnnotationEntry annotEntry = new AnnotationEntry();
			annotEntry.name = annotationClassName.substring(ANNOTATION_PACKAGE.length());

			// If the annotation has any parameters defined, retrieve them and store them.
			@SuppressWarnings("unchecked")
			Set<String> annotationAttribs = annotation.getMemberNames();
			if (annotationAttribs != null) {
				for (String memberName : annotationAttribs) {
					Object memberVal = annotation.getMemberValue(memberName);
					if (memberVal instanceof StringMemberValue) {
						annotEntry.parameters.put(memberName, memberVal.toString());
					}
					else if (memberVal instanceof IntegerMemberValue) {
						annotEntry.parameters.put(memberName, Integer.valueOf(((IntegerMemberValue)memberVal).getValue()));
					}
					else if (memberVal instanceof BooleanMemberValue) {
						annotEntry.parameters.put(memberName, Boolean.valueOf(((BooleanMemberValue)memberVal).getValue()));
					}
					else if (memberVal instanceof EnumMemberValue) {
						Verbs verbs = Verbs.valueOf(((EnumMemberValue)memberVal).getValue());
						annotEntry.parameters.put(memberName, verbs);
					}
					else if (memberVal instanceof ArrayMemberValue) {
						MemberValue[] rawValues = ((ArrayMemberValue)memberVal).getValue();
						// We only accept String arrays for now
						String[] values = new String[rawValues.length];
						for (int i = 0; i < rawValues.length; i++) {
							values[i] = rawValues[i].toString().replaceAll("\"", "");
						}
						annotEntry.parameters.put(memberName, values);
					}
					else {
						throw new RuntimeException(context + " has an annotation with parameter " + memberName + 
								" that is of an unsupported type.");
					}
				}
			}

			res.put(annotEntry.name, annotEntry);
		}
		
		return res;

	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicAnalysis.java 868 2012-02-29 10:34:31Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 