package com.autobizlogic.abl.rule;

import com.autobizlogic.abl.annotations.Verbs;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.logic.Verb;

public class AbstractDependsOnWithVerbsRule extends AbstractDependsOnRule {

	private Verbs verbs = Verbs.ALL;

	
	/**
	 * Get the verbs under which this action should be executed, by default ALL.
	 */
	public Verbs getVerbs() {
		return verbs;
	}
	
	protected void setVerbs(Verbs v) {
		verbs = v;
	}

	public  boolean verbIsRelevant(LogicRunner aLogicRunner) {
		if (getVerbs() == Verbs.ALL)
			return true;
		if (aLogicRunner.getVerb() == Verb.INSERT) {
			return getVerbs() == Verbs.INSERT || 
				getVerbs() == Verbs.INSERT_DELETE ||
				getVerbs() == Verbs.INSERT_UPDATE;
		}
		if (aLogicRunner.getVerb() == Verb.UPDATE) {
			return getVerbs() == Verbs.UPDATE || 
				getVerbs() == Verbs.INSERT_UPDATE ||
				getVerbs() == Verbs.UPDATE_DELETE;
		}
		if (aLogicRunner.getVerb() == Verb.DELETE) {
			return getVerbs() == Verbs.DELETE || 
				getVerbs() == Verbs.INSERT_DELETE ||
				getVerbs() == Verbs.UPDATE_DELETE;
		}

		return false;
	}
	
	@Override
	public String toString() {
		return super.toString() + ", Verbs=" + verbs;
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 