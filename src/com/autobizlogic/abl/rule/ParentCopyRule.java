package com.autobizlogic.abl.rule;

import java.util.Map;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.perf.PerformanceMonitor;
import com.autobizlogic.abl.event.LogicAfterParentCopyEvent;
import com.autobizlogic.abl.session.LogicTransactionContext;
import com.autobizlogic.abl.util.BeanMap;
import com.autobizlogic.abl.util.BeanUtil;
import com.autobizlogic.abl.util.NodalPathUtil;

/**
 * Define and execute a parent copy rule.
 */
public class ParentCopyRule extends AbstractDependsOnRule {
	
	private String roleName;
	
	private String parentAttributeName;
	
	private String childAttributeName;
	
	private boolean noCode;

	protected ParentCopyRule(LogicGroup logicGroup, String logicMethodName, String roleName, 
			String parentAttributeName, String childAttributeName) {
		
		this.logicGroup = logicGroup;
		this.logicMethodName = logicMethodName;
		this.roleName = roleName;
		this.parentAttributeName = parentAttributeName;
		this.childAttributeName = childAttributeName;
	}

	public String getRoleName() {
		return roleName;
	}

	public String getParentAttributeName() {
		return parentAttributeName;
	}

	public String getChildAttributeName() {
		return childAttributeName;
	}

	public boolean isNoCode() {
		return noCode;
	}

	public void setNoCode(boolean noCode) {
		this.noCode = noCode;
	}
	
	@SuppressWarnings("rawtypes")
	public boolean execute(Object aLogicObject, LogicRunner aLogicRunner) {
		long startTime = System.nanoTime();
		PersistentBean priorDomainObject = aLogicRunner.getPriorDomainObject();
		PersistentBean currentDomainObject = aLogicRunner.getCurrentDomainObject();
		MetaRole role = this.getLogicGroup().getMetaEntity().getMetaRole(roleName);
		
		boolean needToRefresh = false;
		boolean newOrphan = false; // Is the child newly orphaned along this role?
		
		if (priorDomainObject == null && currentDomainObject != null)
			needToRefresh = true;
		else if (priorDomainObject != null && currentDomainObject == null) { // This should never happen, but just in case...
			newOrphan = true;
			needToRefresh = true;
		}
		else if (priorDomainObject != null && currentDomainObject != null) {
			Object oldParent = priorDomainObject.get(roleName);
			Object currentParent = currentDomainObject.get(roleName);
			if (oldParent == null && currentParent != null)
				needToRefresh = true;
			else if (oldParent != null && currentParent == null) {
				newOrphan = true;
				needToRefresh = true;
			}
			else if (oldParent != null && currentParent != null) {
				needToRefresh = !BeanUtil.beansAreEqual(role.getOtherMetaEntity(), oldParent, currentParent);
			}
		}
		
		if (needToRefresh) {
			Object parentValue;
			if (newOrphan) {
				parentValue = null;
			}
			else {
				Object currentParent = currentDomainObject.get(roleName);
				if (currentParent == null) {
					parentValue = null;
				}
				else {
					Map parentMap = null;
					if (currentParent instanceof Map)
						parentMap = (Map)currentParent;
					else
						parentMap = new BeanMap(currentParent);
					parentValue = parentMap.get(parentAttributeName);
				}
			}
			if (parentValue != currentDomainObject.get(this.childAttributeName)) {
				Object oldChildValue = currentDomainObject.put(this.childAttributeName, parentValue);
				if ( ! noCode)
					invokeLogicMethod(currentDomainObject, priorDomainObject, aLogicRunner);
				
				if (log.isDebugEnabled()) {
					String realClass = NodalPathUtil.getNodalPathLastName(this.getLogicGroup().getMetaEntity() .getEntityName());					
					log.debug("Refreshing parent reference " + realClass + '.' +  this.childAttributeName + 
							" from " + this.roleName + "." + this.parentAttributeName + ": " + 
							oldChildValue + " -> " + parentValue + " on", aLogicRunner);
				}
				firePostEvent(aLogicObject, aLogicRunner, System.nanoTime() - startTime);
			}
		}
		
		// TODO : have option on the annotation, e.g. nullIfOrphan=true ?
//		if (newOrphan) {
//			// Not sure what to do here?
//			System.out.println("Orphan ParentCopy!");
//		}
		
		return true;
	}
	
	/**
	 * Fire the post event for this rule.
	 */
	protected void firePostEvent(Object aLogicObject, LogicRunner aLogicRunner, long executionTime) {
		LogicAfterParentCopyEvent evt = new LogicAfterParentCopyEvent(aLogicRunner.getContext(), 
				aLogicRunner.getLogicContext(), this, aLogicRunner.getCurrentDomainObject());
		evt.setExecutionTime(executionTime);
		LogicTransactionContext.fireEvent(evt);
		PerformanceMonitor.addRuleExecution(this, executionTime);
	}

	@Override
	public String toString() {
		return "Parent Copy " + roleName + "." + parentAttributeName + " -> " + childAttributeName + super.toString();
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ParentCopyRule.java 1020 2012-03-29 09:56:08Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 