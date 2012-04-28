package com.autobizlogic.abl.logic.analysis;

import java.util.*;

import javassist.CtMethod;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.rule.RuleManager;
import com.autobizlogic.abl.util.NodalPathUtil;

/**
 * Analyzes a method to see if it is relevant to the business logic, and stores the result of that analysis. 
 * This class, given a Java method in the form of a CtMethod, will examine said method and analyze it. 
 * More specifically:
 * <ol>
 * <li>it will see if the method has any annotations. If it does not, it will consider it irrelevant
 * <li>if no annotation is from the com.autobizlogic.abl.businesslogic.annotations package, the
 * method will be considered irrelevant to business logic
 * <li>if the two previous tests are passed, the method's byte code will then be analyzed,
 * looking for calls to instance methods of classes in packages that contain persistent beans
 * (as indicated in ABL's configuration file, or in the default package). If any such calls are found, a dependency to that
 * method will be recorded.
 * <ol>
 */
public class LogicMethodAnalysis extends LogicAnalysis
{
	/**
	 * The different types of business logic methods
	 */
	public enum Type {
		FORMULA,
		CONSTRAINT,
		COMMITCONSTRAINT,
		PARENTCOPY,
		SUM,
		COUNT,
		MINIMUM,
		MAXIMUM,
		EARLYACTION,
		ACTION,
		COMMITACTION
	}
	
	/**
	 * The kind of business logic implemented by this method
	 */
	protected Type type;
	
	/**
	 * Once the class is analyzed, this will contain a reference to all the relevant classes and their properties
	 * upon which the method depends.
	 */
	protected Map<ClassDependency, List<PropertyDependency>> dependencies = 
			new HashMap<ClassDependency, List<PropertyDependency>>();

	/**
	 * This is used to keep track of the intra-class calls to derived attribute methods.
	 * Once the class is analyzed, these will be converted into property dependencies
	 * by copying the called method's dependencies into the caller method's dependencies.
	 */
	protected Set<MethodDependency> methodDependencies = new HashSet<MethodDependency>();
	
	/**
	 * The method to be analyzed by this object.
	 */
	protected CtMethod method;
	
	/**
	 * The LogicClassAnalysis that this object is a part of.
	 */
	protected LogicClassAnalysis classAnalysis;
	
	/**
	 * The name of the method to be analyzed by this object.
	 */
	protected String methodName;
	
	/**
	 * If the annotation that decorates the method has any parameters, they will be stored here.
	 */
	protected Map<String, AnnotationEntry> annotations;
	
	/**
	 * The full name of the return type of the method, or null if the method is void.
	 */
	protected String returnTypeName;
	
	/**
	 * Whether the analysis of the method has already been done.
	 */
	protected boolean analysisPerformed;
	
	/**
	 * Whether the method is actually relevant to business logic, i.e. whether it has proper annotations.
	 */
	protected boolean hasBusinessLogic;
	
	/**
	 * The number of opcodes in this method. This is mostly used to detect empty method for sums and counts.
	 */
	protected int codeSize = 0;
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Protected constructor because instances of this class should really be created only by LogicClassAnalysis.
	 * @param method The method to be analyzed by this object
	 */
	protected LogicMethodAnalysis(LogicClassAnalysis classAnalysis, CtMethod method) {
		this.classAnalysis = classAnalysis;
		this.method = method;
	}
	
	/**
	 * Get the name of the method analyzed by this object
	 * @return The method's name
	 */
	public String getMethodName() {
		if (methodName == null)
			methodName = method.getName();
		return methodName;
	}
	
	public LogicClassAnalysis getClassAnalysis() {
		return classAnalysis;
	}
	
	/**
	 * Get the names and parameters of any annotations for this method.
	 * @return
	 */
	public Map<String, AnnotationEntry> getAnnotations() {
		analyze();
		
		return annotations;
	}
	
	/**
	 * Get all the dependencies for the method.
	 * @return A Map where the key is the class upon which this method depends, and the value
	 * is a set of PropertyDependency objects, one for each attribute (of the class in the key) upon which
	 * this method depends.
	 */
	public Map<ClassDependency, List<PropertyDependency>> getDependencies() {
		analyze();
		
		return dependencies;
	}
	
	/**
	 * Get all the methods on which this method depends.
	 * @return Key is the class upon which this method depends, and the value is a set of
	 * of MethodDependency objects, one for each method of the class in the key upon which
	 * this method depends.
	 */
	public Set<MethodDependency> getMethodDependencies() {
		return methodDependencies;
	}
	
	/**
	 * Tells whether this method has the annotations required to make it an entry point
	 * into the business logic.
	 * @return True if it does, false if the method is not an entry point.
	 */
	public boolean hasBusinessLogic() {
		analyze();
		
		return hasBusinessLogic;
	}
	
	/**
	 * Get the type of this method, i.e. is it a formula, a parent copy, a sum, etc...
	 * @return The type of this method
	 */
	public Type getType() {
		analyze();
		
		return type;
	}
	
	/**
	 * Get the name of the return type for the method.
	 * @return Null if the method is void
	 */
	public String getReturnTypeName() {
		return returnTypeName;
	}
	
	
	/**
	 * Get the number of opcodes in the method. If the number is 1, then
	 * the method is empty.
	 */
	public int getCodeSize() {
		analyze();
		return codeSize;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////
	// Internal stuff
	
	/**
	 * Run the analysis of this method. This will only run once per instance, any call
	 * after the first call will immediately return.
	 * This first checks whether the method passed in the constructor has a proper annotation.
	 * If it does, it then looks at the byte code and looks for calls to instance methods 
	 * that access persistent beans.
	 */
	protected void analyze() {
		if (analysisPerformed)
			return;
		
		analysisPerformed = true;

		annotations = readMethodAnnotations(method);
		
		// No annotations of any kind : this is definitely not of interest
		if (annotations == null || annotations.size() == 0)
			return;
		
		// It does have at least one annotation, but is any one of them the kind we're interested in?
		if (annotations.containsKey("Formula")) {
			type = Type.FORMULA;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("Constraint")) {
			type = Type.CONSTRAINT;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("CommitConstraint")) {
			type = Type.COMMITCONSTRAINT;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("Sum")) {
			type = Type.SUM;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("Count")) {
			type = Type.COUNT;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("Minimum")) {
			type = Type.MINIMUM;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("Maximum")) {
			type = Type.MAXIMUM;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("EarlyAction")) {
			type = Type.EARLYACTION;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("Action")) {
			type = Type.ACTION;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("CommitAction")) {
			type = Type.COMMITACTION;
			hasBusinessLogic = true;
		}
		else if (annotations.containsKey("ParentCopy")) {
			type = Type.PARENTCOPY;
			hasBusinessLogic = true;
		}		
	}
	

	/**
	 * Add a new entry to our list of dependencies
	 * @param className The fully-qualified name of the class
	 * @param propName The name of the property
	 * @param roleName The name of the role through which the property is accessed, if any
	 */
	protected void addPropertyDependency(String className, String propName, String roleName) {
		
		// Sanity check - if the dependency is not on the bean itself, but one of its parents,
		// then there must be a role name
		String myEntityName = getClassAnalysis().getMetaEntity().getEntityName();
		String myEntityClassName = getClassAnalysis().getMetaEntity().getEntityClass().getName();
		if ((!className.equals(myEntityName)) && (!className.equals(myEntityClassName)) && roleName == null)
			throw new DependencyException("Property dependency on parent bean " + className +
					"." + propName + " must have a role");
		
		MetaModel mmodel = getClassAnalysis().getMetaEntity().getMetaModel();
		ClassDependency classDepend = LogicAnalysisManager.getInstance(mmodel).getDependencyForClass(className);
		List<PropertyDependency> propDepends = dependencies.get(classDepend);
		if (propDepends == null)
		{
			propDepends = new Vector<PropertyDependency>();
			dependencies.put(classDepend, propDepends);
		}
		PropertyDependency propDepend = classDepend.getOrCreatePropertyDependency(propName, roleName);
		if ( ! propDepends.contains(propDepend))
			propDepends.add(propDepend);

		// Keep track of the fact that this entity is involved in business logic, whether or not it itself has business logic.
		MetaEntity depMetaEntity = mmodel.getMetaEntity(className);
		RuleManager.getInstance(classAnalysis.getMetaEntity().getMetaModel()).addRelevantEntity(depMetaEntity);
	}
	
	/**
	 * Register the fact that this method is making a call to another method in the same class
	 * that is a derived property method (formula).
	 * @param methName The name of the method
	 */
	protected void addMethodDependency(String methName) {
		MetaModel mmodel = getClassAnalysis().getMetaEntity().getMetaModel();
		ClassDependency classDepend = LogicAnalysisManager.getInstance(mmodel).
				getDependencyForClass(this.getClassAnalysis().getMetaEntity().getEntityName());
		MethodDependency methDepend = classDepend.getOrCreateMethodDependency(methName);
		methodDependencies.add(methDepend);
	}
	
	/**
	 * Determine whether a given class is relevant, namely if it is considered to be one of the
	 * persistent classes of interest.
	 * @param className The full name of the class, e.g. com.foo.MyClass
	 * @return True if the class appears to be relevant, false otherwise
	 */
	protected boolean classIsRelevant(String className) {
		
		// First -- is it a call to the same class?
		if (className.equals(classAnalysis.getLogicClassName()))
			return true;
		
		// Is it a call to the bean?
		if (className.equals(classAnalysis.getMetaEntity().getEntityName()))
			return true;
		
		// Is it a call to the constraint failure?
		if (className.equals("com.autobizlogic.abl.engine.ConstraintFailure"))
			return true;
		
		String pkgName = NodalPathUtil.getNodalPathPrefix(className);

		// Next, if the config file specifies which packages are relevant, use that
		String packageList = LogicConfiguration.getInstance().getProperty(LogicConfiguration.PropertyName.PERSISTENT_PACKAGE_NAMES);
		if (packageList != null) {
			String[] packages = packageList.split(",");
			for (String pkg : packages)
			{
				if (pkgName.equals(pkg.trim()))
					return true;
			}
			return false;
		}
		// No packages specified in config file -- assume only calls within the same package as the bean are relevant
		String beanPkgName = NodalPathUtil.getNodalPathPrefix(classAnalysis.getMetaEntity().getEntityName());
		return pkgName.equals(beanPkgName);
	}
	
	/**
	 * Get all the annotations for a given method
	 * @param ctMeth The method in question
	 * @return A collection of all the annotations for this method. If none, an empty
	 * collection is returned.
	 */
	protected static Map<String, AnnotationEntry> readMethodAnnotations(CtMethod ctMeth) {
		
		// First check whether the method has any of our annotations
		Object[] annots = null;
		try {
			annots = ctMeth.getAnnotations();
		}
		catch (ClassNotFoundException ex)
		{
			throw new DependencyException("Annotation class not found : " + ex.getLocalizedMessage(), ex);
		}
		
		// No annotations of any kind : this is definitely not of interest
		if (annots == null || annots.length == 0)
			return new HashMap<String, AnnotationEntry>();
		
		return readAnnotations(annots,
				"Logic class " + ctMeth.getDeclaringClass().getName() + ", method " + ctMeth.getName());
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////
	// Menial stuff
	
	@Override
	public String toString() {
		return "Method analysis for " + getMethodName();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicMethodAnalysis.java 1120 2012-04-10 22:42:20Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 