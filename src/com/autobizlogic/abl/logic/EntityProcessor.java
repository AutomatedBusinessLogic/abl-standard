package com.autobizlogic.abl.logic;

import com.autobizlogic.abl.data.PersistentBean;

/**
 * This interface can be implemented to intercept persistent objects before
 * and after they get handled by the engine. This was originally developed
 * because the Play framework tends to flush objects out of the session before
 * the rules execute, and therefore the objects need to be reattached to the
 * session before the rules fire, and they need to be saved in a special way
 * afterwards.
 */
public interface EntityProcessor {

	/**
	 * This will get called right before a LogicRunner does its thing.
	 * @param verb What the LogicRunner is about to do
	 * @param bean The persistent object, which should *not* be modified.
	 */
	public void preProcess(Verb verb, PersistentBean bean);
	
	/**
	 * This will get called right after a LogicRunner has done its thing.
	 * @param verb What the LogicRunner just did
	 * @param beanThe persistent object, which *can* be modified if desired.
	 */
	public void postProcess(Verb verb, PersistentBean bean);
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 