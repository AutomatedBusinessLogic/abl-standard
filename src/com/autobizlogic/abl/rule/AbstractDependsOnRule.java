package com.autobizlogic.abl.rule;

import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.logic.LogicSvcs;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.util.NodalPathUtil;
import com.autobizlogic.abl.util.ObjectUtil;

public abstract class AbstractDependsOnRule extends AbstractRule {
	
	
	static boolean isFormulaPruningEnabled = true; // set to false to short-circuit

	/**
	 * <strong>Formula Pruning</strong> - avoid costly parent access in child expression evaluation
	 * when no attributes have changed,  no parent is <em>cascading</em> a Parent Reference, 
	 * and not reparenting.
	 * <p>
	 * 
	 * <strong>Cascade</strong> is explicitly signaled by parent rows who initiate the cascade,
	 * by setting the childRunners:
	 * <ol>
	 * <li><code>logicSource</code> - indicates why update is triggered</li>
	 * <li><code>callingRoleMeta</code> - the cascade's role
	 * </ol>
	 * 
	 * @param aChildRunner child row (origin of request)
	 * @param aDependsList, containing the <em>localAttributes</em> (no ".') and TODO formula object!
	 * <em>parent.attributes</em> that aChildRunner's expression depends on 
	 * @return true if no local attributes changed and not cascading a referenced parent attribute
	 */
	public  boolean isFormulaPrunable(LogicRunner aChildRunner) {

		if (isFormulaPruningDisabled()) {
			return false;
		}
		if (aChildRunner.getVerb() == Verb.INSERT)
			return false;
		
		if (aChildRunner.getVerb() == Verb.DELETE)
			return false;			// TODO needs discussion - do we prune all formulas on delete?
		
		if (this.getDependencies().isEmpty())
			return false;
		
		PersistentBean currentBean = aChildRunner.getCurrentDomainObject();
		MetaEntity currentEntity = currentBean.getMetaEntity();

		for (RuleDependency eachDepend: this.getDependencies()) {
			String roleToParent = eachDepend.getBeanRoleName();  // eg, purchaseorder
			if (roleToParent != null && ! "".equals(roleToParent)) {
				
				// Check whether we've been reparented: if we have, no pruning for us
				Object currentChild = aChildRunner.getCurrentDomainObject();
				Object oldChild = aChildRunner.getPriorDomainObject();
				if (oldChild != null && ObjectUtil.propertyHasChanged(roleToParent, currentChild, oldChild))
					return false;
				
				MetaRole roleToChildren = currentEntity.getMetaRole(roleToParent).getOtherMetaRole();
				if (LogicSvcs.isParentCascadingChangedRefdAttrs(roleToChildren, aChildRunner)) {
					return false;
				}
			} else if (LogicSvcs.isAttributeChanged(aChildRunner, eachDepend.getBeanAttributeName())) {
				return false;
			}
		} 

		return true;
	}


	private boolean isFormulaPruningDisabled() {
		String noPruning = " "; //" EmployeeLogic DepartmentLogic PaymentLogic ProductLogic PurchaseorderLogic PaymentPurchaseorderAllocationLogic ProductBillofmaterialsLogic";
		/*
		 * FIXME - should not be required (make noPruning =" ") with Groovy dependency analysis, yet these fail:
		 * Payment_save_test  
		 * Purchaseorder_update_makeReady_test 
		 * Purchaseorder_update_adjustments_test
		 * Purchaseorder_save_test
		 * Product_save_computeKitPrice_test  due to call on line 63
		 * 
		 * One bug is that PurchaseorderLogic#deriveAmountDiscounted not called; patching that fixes:
		 * BasicFormulaUpdate
		 * Purchaseorder_clone_test
		 */
		String logicClassName = getLogicGroup().getLogicClassName();
		logicClassName = NodalPathUtil.getNodalPathLastName(logicClassName);
		if (noPruning.indexOf(logicClassName) > -1)
			return true;
		if (! isFormulaPruningEnabled)
			return true;
		return false;
	}


	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  AbstractDependsOnRule.java 1112 2012-04-09 01:20:12Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 