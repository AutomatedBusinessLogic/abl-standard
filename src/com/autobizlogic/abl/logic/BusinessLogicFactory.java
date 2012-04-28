package com.autobizlogic.abl.logic;

import com.autobizlogic.abl.logic.LogicSource;
import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.metadata.MetaRole;
import com.autobizlogic.abl.session.LogicTransactionContext;

public interface BusinessLogicFactory {
	
	/**
	 * Factory, providing flexibility/extensibility for associating a <br>
	 * Business Logic Component with a domain (pojo) class.
	 * <p>
	 * 
	 * The default factory computes the Business Logic Component from the domain class.
	 * <blockquote>
	 *  For domain objects in <code>com.app.Customer</code>, <br>
	 *  the Business Logic Component is <code>com.app.Customer<em>Logic</em></code>
	 * </blockquote>
	 * You can use the factory to override this on a general or per-instance basis.<br>
	 * For example, you might have a single domain class PurchaseOrder, <br>
	 * and desire different logic for retail vs wholesale orders.  You can
	 * <ol>
	 * <li>Define a common shared Business Logic Component <code>PurchaseOrderLogicBase</code>
	 * <li>Extend this with type-specific logic with <code>PurchaseOrderLogicRetail</code> <br>
	 * and <code>PurchaseOrderLogicWholesale</code>
	 * <li>Implement this factory to choose between the latter two, based on <code>aDomainObject.attribute</code>
	 * </ol>
	 * 
	 * @param aDomainObject Hibernate POJO instance (e.g, a PurchaseOrder bean)
	 * @return instance of logic class (e.g., PurchaseOrderLogic) for aDomainObject (or null)
	 */
	public Object createLogicObjectForDomainObject (PersistentBean persBean);
	
	
	/**
	 * Create a LogicRunner for the given context and state.
	 * @param aLogicTransactionContext
	 * @param currentObjectState Current state of the object
	 * @param priorObjectState Prior state of the object, can be null for inserts
	 * @param aVerb - insert, update or delete
	 * @param aLogicSource
	 * @param aCallingLogicRunner
	 * @param aCallingRoleMeta
	 * @return returns instance that can execute the rules for currentObjectState
	 */
	public LogicRunner createLogicRunner(LogicTransactionContext aLogicTransactionContext, PersistentBean currentState, 
			PersistentBean priorState, Verb aVerb, LogicSource aLogicSource, LogicRunner aCallingLogicRunner, MetaRole aCallingRoleMeta);
	
	/**
	 * Find or create a LogicRunner for the given context and state.
	 * @param aLogicTransactionContext
	 * @param currentState
	 * @param priorState
	 * @param aVerb
	 * @param aLogicSource
	 * @param aCallingLogicRunner
	 * @param aCallingRoleMeta
	 * @return If this returns null, it means that the LogicContext should not be dealt with any
	 * longer. This happens if we determine that the LogicRunner will be run soon anyway, or if we 
	 * have created and queued it up for execution.
	 */
	public LogicRunner getLogicRunner(LogicTransactionContext aLogicTransactionContext, PersistentBean currentState, 
			PersistentBean priorState, Verb aVerb, LogicSource aLogicSource, LogicRunner aCallingLogicRunner, MetaRole aCallingRoleMeta);
	
	/**
	 * factory
	 * @return new (empty) LogicContext
	 * @see com.autobizlogic.abl.context.LogicContext
	 */
	public LogicContext createLogicContext();


	/**
	 * 
	 * @param aLogicContext
	 * @return useCaseName (either explicit per attr named useCase[Name], or implicit as Object_verb)
	 */
	public String computeUseCaseName(LogicContext aLogicContext);

}
/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 