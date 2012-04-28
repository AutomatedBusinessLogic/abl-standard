package com.autobizlogic.abl.engine.phase;

import com.autobizlogic.abl.engine.LogicException;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;


public class LogicPhaseBase {
	
	/**
	 * must override in subclass
	 */
	LogicRunnerPhase logicPhase = null; 
	
	LogicRunner logicRunner = null;
	
	/**
	 * super constructor ensures phase is set
	 * @param aLogicRunner
	 */
	public LogicPhaseBase(LogicRunner aLogicRunner) {
		super();
		logicRunner = aLogicRunner;
		setLogicPhase();   // must override in subclass
		logicRunner.logicPhase = logicPhase;
	}
	
	@SuppressWarnings("static-method")
	public void setLogicPhase() {
		throw new LogicException("Logic Runner Phase classes must initialize 'executionState'");
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
 