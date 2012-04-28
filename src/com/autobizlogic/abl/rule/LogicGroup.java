package com.autobizlogic.abl.rule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.autobizlogic.abl.annotations.Verbs;
import com.autobizlogic.abl.logic.analysis.AnnotationEntry;
import com.autobizlogic.abl.logic.analysis.ClassDependency;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.logic.analysis.LogicClassAnalysis;
import com.autobizlogic.abl.logic.analysis.LogicFieldAnalysis;
import com.autobizlogic.abl.logic.analysis.LogicMethodAnalysis;
import com.autobizlogic.abl.logic.analysis.PropertyDependency;
import com.autobizlogic.abl.logic.analysis.LogicAnalysisManager;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaModel;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.StringUtil;

/**
 * Represents a collection of business logic, which for now means a business logic class.
 * This class is fairly complicated and needs to be reorganized.
 */

public class LogicGroup {

	/**
	 * The full class name of the class holding these bits of logic.
	 */
	protected String logicClassName;
	
	/**
	 * The MetaEntity for which this contains the logic.
	 */
	protected MetaEntity metaEntity;
	
	/**
	 * The MetaModel to which the MetaEntity belongs (simplifies a lot of code).
	 */
	protected MetaModel metaModel;

	/**
	 * The LogicAnalysisManager for this meta model. We keep it here just to simplify code.
	 */
	protected LogicAnalysisManager lam;
	
	/**
	 * LogicClass is Groovy.
	 */
	private boolean isGroovy = false;

	/**
	 * The formulas contained by this object.
	 */
	protected Set<FormulaRule> formulas = null;

	/**
	 * The formulas ordered according to their dependencies
	 */
	private List<FormulaRule> orderedFormulas;
	private Object orderedFormulasFlag = new Object();

	/**
	 * The aggregates contained by this object
	 */
	protected Set<AbstractAggregateRule> aggregates = null;
	private Object aggregatesFlag = new Object();

	/**
	 * Keep track of which attributes are derived by which rule. Obviously an attribute
	 * cannot be derived by more than one rule.
	 */
	protected Map<String, AbstractRule> derivations = new HashMap<String, AbstractRule>();

	/**
	 * The constraints contained by this object
	 */
	Set<ConstraintRule> constraints = null;
	private Object constraintsFlag = new Object();

	/**
	 * The commit-time constraints contained by this object.
	 */
	/* package */ Set<CommitConstraintRule> commitConstraints = null;
	private Object commitConstraintsFlag = new Object();
	
	/**
	 * The EarlyActions contained by this object.
	 */
	private Set<EarlyActionRule> earlyActions = null;
	private Object earlyActionsFlag = new Object();

	/**
	 * The actions contained by this object
	 */
	private Set<ActionRule> actions = null;
	private Object actionsFlag = new Object();

	/**
	 * The commit-time actions contained by this object
	 */
	private Set<CommitActionRule> commitActions = null;
	private Object commitActionsFlag = new Object();

	/**
	 * The parent copy rules contained by this object
	 */
	private Set<ParentCopyRule> parentCopies = null;
	private Object parentCopiesFlag = new Object();
	
	/**
	 * The set of all the rules in this LogicGroup. Gets filled on demand.
	 */
	private Set<AbstractRule> allRules = null;

	/**
	 * The name of the field that should be set to the current bean.
	 */
	private String currentBeanFieldName;

	/**
	 * The name of the field (if any) that should be set to the old bean.
	 */
	private String oldBeanFieldName;

	/**
	 * The name of the field (if any) that should be set to the original bean.
	 */
	private String originalBeanFieldName;

	/**
	 * The name of the field (if any) that should be set to the LogicContext.
	 */
	private String contextFieldName;

	/**
	 * This is used to keep track of the dependencies for each rule as it is created.
	 * It is then used later to figure out all the dependencies between the rules.
	 * Obviously we can't determine the dependencies between the rules until we've actually
	 * created the rules.
	 */
	protected Map<AbstractRule, Set<PropertyDependency>> dependencies = 
			new HashMap<AbstractRule, Set<PropertyDependency>>();

	/**
	 * Keep track of which logic method corresponds to a derivation for an attribute.
	 * The key is the name of the logic method, the value is the name of the derived attribute.
	 */
	protected Map<String, String> methodDerivationMap = new HashMap<String, String>();

	/**
	 * Remember when we've done our analysis.
	 */
	private boolean analysisDone = false;

	@SuppressWarnings("unused")
	private final static LogicLogger log = LogicLogger.getLogger(LogicLogger.LoggerName.DEPENDENCY);
	
	////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * The constructor is protected because instances should be retrieved from RuleManager.
	 * @param logicClassName The name of the class implementing the business logic
	 * @param beanClassName The name of the bean class
	 */
	protected LogicGroup(String logicClassName, MetaEntity metaEntity) {

		this.logicClassName = logicClassName;
		this.metaEntity = metaEntity;
		this.metaModel = metaEntity.getMetaModel();
		this.lam = LogicAnalysisManager.getInstance(metaModel);
	}

	/**
	 * Get the full name of the class that contains this business logic, e.g.
	 * com.foo.businesslogic.CustomerLogic
	 */
	public String getLogicClassName() {
		return logicClassName;
	}

	/**
	 * Get the MetaEntity for which this contains the logic.
	 */
	public MetaEntity getMetaEntity() {
		return metaEntity;
	}

	/**
	 * Get the name of the field (if any) that should be set to the current bean
	 */
	public String getCurrentBeanFieldName() {
		analyze();
		return currentBeanFieldName;
	}

	/**
	 * Get the name of the field (if any) that should be set to the old bean
	 */
	public String getOldBeanFieldName() {
		analyze();
		return oldBeanFieldName;
	}

	/**
	 * Get the name of the field (if any) that should be set to the original bean
	 */
	public String getOriginalBeanFieldName() {
		analyze();
		return originalBeanFieldName;
	}

	/**
	 * Get the name of the field (if any) that should be set to the current LogicTransactionContext
	 */
	public String getContextFieldName() {
		analyze();
		return contextFieldName;
	}

	/**
	 * Get the unordered set of all aggregates (sums and counts)
	 * @return
	 */
	public Set<AbstractAggregateRule> getAggregates() {
		if (aggregates != null)
			return aggregates;

		synchronized(aggregatesFlag) {
			if (aggregates == null)
				createAggregates();
		}

		return aggregates;
	}

	/**
	 * Get all the aggregates for a given role
	 * @param roleName The role in question
	 * @return All the aggregate rules that depend on this role. The returned collection can be empty but not null.
	 */
	public Set<AbstractAggregateRule> findAggregatesForRole(String roleName) {
		Set<AbstractAggregateRule> result = new HashSet<AbstractAggregateRule>();
		Set<AbstractAggregateRule> allAggregates = getAggregates();
		for (AbstractAggregateRule aggregate: allAggregates) {
			if (aggregate.getRoleName().equals(roleName))
				result.add(aggregate);
		}
		return result;
	}
	
	/**
	 * Get all the rules defined for this LogicGroup
	 */
	public Set<AbstractRule> getAllRules() {
		if (allRules != null)
			return allRules;
		
		synchronized(this) {
			if (allRules != null)
				return allRules;
			
			allRules = new HashSet<AbstractRule>();
			
			allRules.addAll(getActions());
			allRules.addAll(getAggregates());
			allRules.addAll(getCommitActions());
			allRules.addAll(getCommitConstraints());
			allRules.addAll(getConstraints());
			allRules.addAll(getEarlyActions());
			allRules.addAll(getFormulas());
			allRules.addAll(getParentCopies());
		}
		
		return allRules;
	}

	/**
	 * Get all the constraints for this class (not including commit-time constraints).
	 */
	public Set<ConstraintRule> getConstraints() {
		if (constraints != null)
			return constraints;

		synchronized(constraintsFlag) {
			if (constraints == null)
				createConstraints();
		}

		return constraints;
	}

	/**
	 * Get all the commit-time constraints for this class.
	 */
	public Set<CommitConstraintRule> getCommitConstraints() {
		if (commitConstraints != null)
			return commitConstraints;

		synchronized(commitConstraintsFlag) {
			if (commitConstraints == null)
				createCommitConstraints();
		}

		return commitConstraints;
	}
	
	/**
	 * Get all the EarlyActions for this class.
	 */
	public Set<EarlyActionRule> getEarlyActions() {
		if (earlyActions != null)
			return earlyActions;

		synchronized(earlyActionsFlag) {
			if (earlyActions == null)
				createEarlyActions();
		}

		return earlyActions;
	}	

	/**
	 * Get all the actions for this class (not including commit-time actions).
	 */
	public Set<ActionRule> getActions() {
		if (actions != null)
			return actions;

		synchronized(actionsFlag) {
			if (actions == null)
				createActions();
		}

		return actions;
	}

	/**
	 * Get all the commit-time actions for this class.
	 */
	public Set<CommitActionRule> getCommitActions() {
		if (commitActions != null)
			return commitActions;

		synchronized(commitActionsFlag) {
			if (commitActions == null)
				createCommitActions();
		}

		return commitActions;
	}
	
	public Set<ParentCopyRule> getParentCopies() {
		if (parentCopies != null)
			return parentCopies;
		
		synchronized(parentCopiesFlag) {
			if (parentCopies == null)
				createParentCopies();
		}
		
		return parentCopies;
	}

	/**
	 * Get a list of the formulas in the order in which they should be executed.
	 * @return The list of all the derivations that need to be executed, in the proper order.
	 * The return value will be an empty list if there are no formulas.
	 */
	public List<FormulaRule> getFormulas() {
		if (orderedFormulas != null)
			return orderedFormulas;
		
		synchronized(orderedFormulasFlag) {
			if (orderedFormulas == null)
				loadFormulas();
		}
		
		return orderedFormulas;
	}
	
	/**
	 * Read all the formulas from the logic class.
	 */
	private List<FormulaRule> loadFormulas() {
		if (orderedFormulas != null)
			return orderedFormulas;

		// We need to create both the formulas *and* the sums and counts so that the dependencies
		// work out OK. This is only because we want to support formulas calling local methods
		// instead of bean getters.
		createFormulas();
		createAggregates();

		List<FormulaRule> newOrderedFormulas = new Vector<FormulaRule>();

		Map<AbstractRule, Set<RuleDependency>> dependGraph = new HashMap<AbstractRule, Set<RuleDependency>>();

		// Add all the formulas to the dependency graph
		for (FormulaRule formula : formulas) {
			Set<RuleDependency> thingsThisDependsOn = getDependenciesForRule(formula);
			dependGraph.put(formula, thingsThisDependsOn);
			for (RuleDependency dep : thingsThisDependsOn) {
				formula.addDependency(dep);
			}
		}

		// Now determine the dependencies and order the rules accordingly
		int numIters = 0;
		Set<FormulaRule> resolvedRules = new HashSet<FormulaRule>();
		while (dependGraph.size() > 0) {
			for (AbstractRule absRule : dependGraph.keySet()) {
				if ( ! (absRule instanceof FormulaRule))
					continue;
				FormulaRule rule = (FormulaRule)absRule;
				
				Set<RuleDependency> depends = dependGraph.get(rule);

				// If the rule has no dependency, mark it as resolved and move on
				if (depends.size() == 0) {
					newOrderedFormulas.add(rule);
					resolvedRules.add(rule);
					continue;
				}

				// Check each dependency: is it for a derived attribute? If so, then have we already
				// resolved that attribute?
				Set<RuleDependency> resolvedDependencies = new HashSet<RuleDependency>();
				for (RuleDependency dep : depends) {
					AbstractRule depRule = RuleManager.getInstance(metaEntity.getMetaModel())
							.getDerivationRuleForAttribute(dep.getBeanClassName(), dep.getBeanAttributeName());
					
					// If the attribute is not derived, or its derivation is already resolved, dependency is not an issue
					if (depRule == null || dependGraph.get(depRule) == null ||
							resolvedRules.contains(depRule)) { 
						resolvedDependencies.add(dep);
						continue;
					}
					// Self-dependency is allowed so long as it is through a role
					if (depRule.equals(rule) && dep.getBeanRoleName() != null) {
						resolvedDependencies.add(dep);
						continue;
					}
					if (resolvedRules.contains(depRule)) {
						resolvedDependencies.add(dep);
						continue;
					}
				}
				depends.removeAll(resolvedDependencies);
				if (depends.size() == 0) {
					newOrderedFormulas.add(rule);
					resolvedRules.add(rule);
				}
			}

			// Now remove the resolved rules from the dependency graph
			for (AbstractRule rule : resolvedRules) {
				dependGraph.remove(rule);
			}

			// Sanity check
			numIters++;
			if (numIters > 5000) {
				StringBuffer sb = new StringBuffer();
				for (AbstractRule rule : dependGraph.keySet()) {
					if (sb.length() > 0)
						sb.append(", ");
					sb.append(rule.getLogicGroup().getLogicClassName() + "#" + rule.getLogicMethodName());
				}
				throw new RuntimeException("Circular dependency detected in logic class " + this.getLogicClassName() + 
						" most likely involving one or more of the following rule(s) : " + sb.toString());
			}
		}
		
		orderedFormulas = newOrderedFormulas;
		
		return orderedFormulas;
	}

	/**
	 * Get the rule that derives the value for the given attribute.
	 * @param attributeName The name of the attribute, e.g. orderTotal
	 * @return The rule in question (FormulaRule, SumRule or CountRule), or null if the attribute
	 * does not exist or is not derived.
	 */
	public AbstractRule getDerivationForAttribute(String attributeName) {
		getFormulas();
		getAggregates();
		getParentCopies();
		return derivations.get(attributeName);
	}

	/**
	 * Is this logic class written in Groovy?
	 */
	public boolean isGroovy() {
		return isGroovy;
	}
	
	/**
	 * Returns all the aggregate rules that rely on the given parent-to-child role.
	 */
	public Set<AbstractAggregateRule> findAggregatesForRole(MetaRole role) {
		
		Set<AbstractAggregateRule> result = new HashSet<AbstractAggregateRule>();
		Set<AbstractAggregateRule> theAggregates = getAggregates();
		for (AbstractAggregateRule aggregate : theAggregates) {
			if (aggregate.getRole().equals(role))
				result.add(aggregate);
		}
		return result;
	}
	
	/**
	 * Find all the attributes of an entity that are referenced somehow by a child entity
	 * through the given role.
	 * @return An empty set if none
	 */
	public Set<String> getAttributesReferencedThroughRole(MetaRole role) {
		
		String roleName = role.getRoleName();
		String entityName = this.getMetaEntity().getEntityName();
		String parentEntityName = role.getOtherMetaEntity().getEntityName();
		if (attributesReferencedThroughRole.containsKey(roleName))
			return attributesReferencedThroughRole.get(roleName);
		
		Set<String> result = new HashSet<String>();
		
		// Quick sanity check
		if (role.isCollection())
			throw new RuntimeException("Cannot call getAttributesReferencedThroughRole on " +
					"collection role: " + role);
		if ( ! role.getMetaEntity().equals(this.getMetaEntity()))
			throw new RuntimeException("Internal error: getAttributesReferencedThroughRole was called for " +
					"role [" + role + "] which does not belong to entity " + entityName);
		
		Set<AbstractRule> childRules = getAllRules();
		for (AbstractRule rule : childRules) {
			for (RuleDependency depend : rule.getDependencies()) {
				if (depend.getBeanRoleName() == null)
					continue;
				
				if (depend.getBeanClassName().equals(parentEntityName) && 
						depend.getBeanRoleName().equals(roleName)) {
					result.add(depend.getBeanAttributeName());
				}
			}
		}
		
		attributesReferencedThroughRole.put(roleName, result);
		return result;
	}
	
	/**
	 * Used to cache the results of getAttributesReferencedThroughRole
	 */
	private Map<String, Set<String>> attributesReferencedThroughRole =
			new ConcurrentHashMap<String, Set<String>>();
	
	///////////////////////////////////////////////////////////////////////////////////////
	// Internal methods

	/**
	 * Do some high-level analysis of the logic class
	 */
	private synchronized void analyze() {
		if (analysisDone)
			return;

		analysisDone = true;

		LogicClassAnalysis classAnalysis = lam.getLogicAnalysisForEntity(metaEntity);

		for (LogicFieldAnalysis fldAnalysis : classAnalysis.getFieldAnalyses()) {
			// If it is a field for the bean, make sure it's the right type
			if (fldAnalysis.getAnnotations().containsKey("CurrentBean") || fldAnalysis.getAnnotations().containsKey("OldBean") ||
					fldAnalysis.getAnnotations().containsKey("OriginalBean")) {
				if (metaEntity.isPojo()) {
					if ( ! fldAnalysis.getFieldClassName().equals(metaEntity.getEntityClass().getName())) {
						// Not the same type -- could it be a subclass?
						Class<?> fieldCls = ClassLoaderManager.getInstance().getClassFromName(fldAnalysis.getFieldCtClass().getName());
						if ( ! fieldCls.isAssignableFrom(metaEntity.getEntityClass())) {
							throw new RuntimeException("Logic class " + logicClassName + " has a variable called " + 
									fldAnalysis.getFieldName() +
									" which should contain a persistent bean, but the variable is not of the correct type, " +
									"which is " + metaEntity.getEntityClass().getName());
						}
					}
				}
				else {
					if ( ! fldAnalysis.getFieldClassName().equals("java.util.Map"))
						throw new RuntimeException("Logic class " + logicClassName + " has a variable called " + 
								fldAnalysis.getFieldName() + " which should contain a persistent bean, but " +
								"the variable is not of the correct type, which is Map<String, Object>");
				}
			}

			if (fldAnalysis.getAnnotations().containsKey("CurrentBean")) {
				currentBeanFieldName = fldAnalysis.getFieldName();
			}
			else if (fldAnalysis.getAnnotations().containsKey("OldBean")) {
				oldBeanFieldName = fldAnalysis.getFieldName();
			}
			else if (fldAnalysis.getAnnotations().containsKey("OriginalBean")) {
				originalBeanFieldName = fldAnalysis.getFieldName();
			}
			else if (fldAnalysis.getAnnotations().containsKey("LogicContextObject")) {
				contextFieldName = fldAnalysis.getFieldName();
				if ( ! "com.autobizlogic.abl.logic.LogicContext".equals(fldAnalysis.getFieldClassName()))
					throw new RuntimeException("Logic class " + logicClassName + " has a variable called " + 
							fldAnalysis.getFieldName() + " which is annotated to contain the LogicContext, " +
							"but its type is not LogicContext.");
			}
			isGroovy = classAnalysis.isGroovy();
		}
	}

	/**
	 * Given a rule, get all the dependencies it has on other attributes
	 * @param rule The rule in question
	 * @return
	 */
	/* package */ Set<RuleDependency> getDependenciesForRule(AbstractRule rule) {

		Set<RuleDependency> thingsThisDependsOn = new HashSet<RuleDependency>();
		Set<PropertyDependency> depends = dependencies.get(rule);
		if (depends == null)
			return thingsThisDependsOn;
		
		for (PropertyDependency methDep : depends) {
			RuleDependency dep = new RuleDependency(methDep.getClassDependency().getClassName(), 
					methDep.getPropertyName(), methDep.getRoleName());
			thingsThisDependsOn.add(dep);
		}
		
		return thingsThisDependsOn;
	}

	/**
	 * Get the attribute name from a LogicMethodAnalysis for a formula, parent copy, sum or count. 
	 * This looks at the annotation first, and if there is a
	 * parameter named "attributeName", it will return that. If not, it will derive the attribute name from
	 * the name of the method, e.g. deriveTotal would yield "total".
	 * @param methodAnalysis The analysis for the method in question
	 * @return The name of the bean attribute derived by this method
	 */
	protected String getBeanAttributeName(LogicMethodAnalysis methodAnalysis, String annotationName) {

		// First figure out which bean attribute this rule is for
		String beanAttributeName = null;

		// Is it specified as an annotation?
		Map<String, AnnotationEntry> annots = methodAnalysis.getAnnotations();
		AnnotationEntry annot = annots.get(annotationName);
		if (annot == null)
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is supposed to be a " + annotationName + 
					" but does not have the right annotation. This should not happen.");
		beanAttributeName = StringUtil.stripSurroundingDoubleQuotes((String)annot.parameters.get("attributeName"));
		if (beanAttributeName == null) {
			// Not specified as an annotation - it should be derived from the name of the logic method
			if ( ! methodAnalysis.getMethodName().startsWith("derive")) {
				throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
						" which is marked as a " + annotationName + ", but its name does not conform to " +
						"the convention e.g. deriveMyAttribute where myAttribute would be the name of the attribute.");
			}

			String rawName = methodAnalysis.getMethodName().substring("derive".length());
			if (rawName.length() == 0) {
				throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
						" which is marked as a " + annotationName + ", but its name does not conform to the convention " +
						"e.g. deriveMyAttribute where myAttribute would be the name of the attribute.");
			}
			beanAttributeName = Character.toLowerCase(rawName.charAt(0)) + rawName.substring(1);
		}

		// Verify that the bean does in fact have such an attribute
		if (metaEntity.getMetaAttribute(beanAttributeName) == null)
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a " + annotationName + ", but is supposed to derive the value of attribute " + 
					beanAttributeName + " which does not seem to exist in the persistent bean " + metaEntity.getEntityName());

		return beanAttributeName;
	}

	/**
	 * Analyze the logic and create the formulas out of it.
	 */
	/* package */synchronized void createFormulas() {

		// If we've already done this, no point in doing it again
		if (formulas != null)
			return;

		FormulaMaker formulaMaker = new FormulaMaker(this);
		formulas = formulaMaker.createFormulas();
	}

	/**
	 * Go over all the methods and select those that define sums, counts, etc..., and create the corresponding
	 * objects.
	 */
	/* package */ synchronized void createAggregates() {
		if (aggregates != null)
			return;
		
		AggregateMaker aggMaker = new AggregateMaker(this);
		aggregates = aggMaker.createAggregates();
	}

	/**
	 * Go over all the methods, select those that define constraints, and create the corresponding
	 * ConstraintRule objects.
	 */
	private void createConstraints() {
		if (constraints != null)
			return;
		
		ConstraintMaker constraintMaker = new ConstraintMaker(this);
		constraints = constraintMaker.createConstraints();
	}

	private void createCommitConstraints() {
		if (commitConstraints != null)
			return;
		
		ConstraintMaker constraintMaker = new ConstraintMaker(this);
		commitConstraints = constraintMaker.createCommitConstraints();
	}
	
	/**
	 * Go over all the methods and create the actions for those methods that marked as such.
	 */
	private synchronized void createEarlyActions() {
		if (earlyActions != null)
			return;

		// For the dependency analysis to work, we need to figure out all the derived attributes first
		createFormulas();
		createAggregates();

		Set<EarlyActionRule> newEarlyActions = new HashSet<EarlyActionRule>();

		LogicClassAnalysis classAnalysis = lam.getLogicAnalysisForEntity(metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.EARLYACTION)
				continue;

			EarlyActionRule earlyAction = (EarlyActionRule)createAction(methodAnalysis, false, true);
			if (earlyAction != null)
				newEarlyActions.add(earlyAction);
		}
		
		earlyActions = newEarlyActions;
	}

	/**
	 * Go over all the methods and create the actions for those methods that marked as such.
	 */
	private synchronized void createActions() {
		if (actions != null)
			return;

		// For the dependency analysis to work, we need to figure out all the derived attributes first
		createFormulas();
		createAggregates();

		Set<ActionRule> newActions = new HashSet<ActionRule>();

		LogicClassAnalysis classAnalysis = lam.getLogicAnalysisForEntity(metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.ACTION)
				continue;

			ActionRule action = createAction(methodAnalysis, false, false);
			if (action != null)
				newActions.add(action);
		}
		
		actions = newActions;
	}

	/**
	 * Go over all the methods and create the actions for those methods that marked as such.
	 */
	private synchronized void createCommitActions() {
		if (commitActions != null)
			return;

		// For the dependency analysis to work, we need to figure out all the derived attributes first
		createFormulas();
		createAggregates();

		Set<CommitActionRule> newCommitActions = new HashSet<CommitActionRule>();

		LogicClassAnalysis classAnalysis = lam.getLogicAnalysisForEntity(metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.COMMITACTION)
				continue;

			CommitActionRule action = (CommitActionRule)createAction(methodAnalysis, true, false);
			if (action != null)
				newCommitActions.add(action);
		}
		
		commitActions = newCommitActions;
	}

	/**
	 * Create an action based on a method analysis. This will throw an exception if the method has a non-void
	 * return type.
	 * @param methodAnalysis The method analysis in question
	 * @param commitTime If true, create a CommitActionRule
	 * @return The newly create action object
	 */
	private ActionRule createAction(LogicMethodAnalysis methodAnalysis, boolean commitTime, boolean earlyAction) { // Not crazy about this signature...
		// Verify that the method is void
		String returnType = methodAnalysis.getReturnTypeName();
		if (returnType != null && !returnType.equals("void"))
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is defined as an action, but it has a non-void return type. All actions must be declared as void.");

		ActionRule action;
		if (commitTime)
			action = new CommitActionRule(this, methodAnalysis.getMethodName());
		else if (earlyAction)
			action = new EarlyActionRule(this, methodAnalysis.getMethodName());
		else
			action = new ActionRule(this, methodAnalysis.getMethodName());
		
		AnnotationEntry annot = methodAnalysis.getAnnotations().get("Action");
		if (annot == null)
			annot = methodAnalysis.getAnnotations().get("CommitAction");
		if (annot == null)
			annot = methodAnalysis.getAnnotations().get("EarlyAction");
		if (annot == null)
			throw new RuntimeException("Logic annotation for method " + logicClassName + "." +
					methodAnalysis.getMethodName() + " is of an unknown action type");
		Verbs verbs = (Verbs)annot.parameters.get("verbs");
		if (verbs == null)
			verbs = Verbs.ALL;
		action.setVerbs(verbs);

		Set<PropertyDependency> propDepends = new HashSet<PropertyDependency>();
		for (List<PropertyDependency> deps : methodAnalysis.getDependencies().values()) {
			propDepends.addAll(deps);
		}
		dependencies.put(action, propDepends);
		Set<RuleDependency> depends = getDependenciesForRule(action);
		for (RuleDependency dep : depends) {
			action.addDependency(dep);
		}

		return action;
	}
	
	/**
	 * Go over all the methods and create the ParentCopy rules for those methods that are annotated as such.
	 */
	private void createParentCopies() {
		
		// If we've already done this, no point in doing it again
		if (parentCopies != null)
			return;

		Set<ParentCopyRule> newParentCopies = new HashSet<ParentCopyRule>();

		LogicClassAnalysis classAnalysis = lam.getLogicAnalysisForEntity(metaEntity);
		Set<LogicMethodAnalysis> methodAnalyses = classAnalysis.getMethodAnalyses();
		for (LogicMethodAnalysis methodAnalysis : methodAnalyses) {
			if (methodAnalysis.getType() != LogicMethodAnalysis.Type.PARENTCOPY)
				continue;

			ParentCopyRule theParentCopy = createParentCopy(methodAnalysis);
			if (theParentCopy != null)
				newParentCopies.add(theParentCopy);
		}
		
		parentCopies = newParentCopies;
	}
	
	private ParentCopyRule createParentCopy(LogicMethodAnalysis methodAnalysis) {
		// First figure out which bean attribute this formula is for
		String childAttributeName = getBeanAttributeName(methodAnalysis, "ParentCopy");

		// Verify that the method is void
		String returnType = methodAnalysis.getReturnTypeName();
		if ( ! (returnType == null || "void".equals(returnType)))
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a ParentCopy, but is not void. It must be void to be a ParentCopy.");
		
		AnnotationEntry parentCopyAnnotation = methodAnalysis.getAnnotations().get("ParentCopy");
		
		String value = StringUtil.stripSurroundingDoubleQuotes((String)parentCopyAnnotation.parameters.get("value"));
		if (value == null || value.trim().length() == 0)
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a parent copy, but it has no specification (e.g. rolename.attributename)");
		value = value.trim();
		
		String roleName = null;
		String parentAttributeName = null;
		int dotIdx = value.indexOf('.');
		if (dotIdx == -1)
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a parent copy, but the specification [" + value + "] is not in " +
					"a valid format (rolename.attributename)");
		roleName = value.substring(0, dotIdx).trim();
		parentAttributeName = value.substring(dotIdx + 1).trim();
		
		if (roleName.length() == 0 || parentAttributeName.length() == 0)
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
			" which is marked as a parent copy, but the specification [" + value + "] is not in a valid " +
					"format (rolename.attributename)");
		
		// Verify that the role exists
		MetaRole metaRole = metaEntity.getMetaRole(roleName);
		if (metaRole == null)
			throw new RuntimeException("Logic class " + logicClassName + " has method " + methodAnalysis.getMethodName() +
					" which is marked as a parent copy, but its role name \"" + roleName + "\" does not seem to exist in the bean.");

		// Now create the rule, with all required information
		ParentCopyRule parentCopy = new ParentCopyRule(this, methodAnalysis.getMethodName(), roleName, 
				parentAttributeName, childAttributeName);
		if (methodAnalysis.getCodeSize() == 1)
			parentCopy.setNoCode(true);
		
		// Add it to the lookup table
		RuleManager.getInstance(metaModel).addDerivationRuleForAttribute(metaEntity.getEntityName(), childAttributeName, parentCopy);

		if (derivations.containsKey(childAttributeName)) {
			AbstractRule rule = derivations.get(childAttributeName);
			throw new RuntimeException("Parent copy rule " + this.getLogicClassName() + "." + methodAnalysis.getMethodName() +
					" is supposed to derive the value of attribute " + childAttributeName + ", but that attribute's value " +
					"is also derived by " + rule.getLogicMethodName() + ". An attribute can be derived only by one method.");
		}
		derivations.put(childAttributeName, parentCopy);

		// Remember the dependencies for later, when we figure them all out
		ClassDependency classDep = lam.getDependencyForClass(metaEntity.getEntityName());
		PropertyDependency methDep = classDep.getOrCreatePropertyDependency(methodAnalysis.getMethodName(), roleName);		
		Set<PropertyDependency> propDepends = new HashSet<PropertyDependency>();
		propDepends.add(methDep);

		dependencies.put(parentCopy, propDepends);
		
		methodDerivationMap.put(methodAnalysis.getMethodName(), childAttributeName);
		
		RuleDependency depend = new RuleDependency(metaRole.getOtherMetaEntity().getEntityName(),
				parentAttributeName, metaRole.getRoleName());
		parentCopy.addDependency(depend);
		
		return parentCopy;
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////
	// Mundane stuff
	
	@Override
	public String toString() {
		return "LogicGroup for entity " + metaEntity.getEntityName();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicGroup.java 1056 2012-04-03 21:40:50Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 