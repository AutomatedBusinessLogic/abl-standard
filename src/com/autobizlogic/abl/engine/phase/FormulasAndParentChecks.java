package com.autobizlogic.abl.engine.phase;

import java.util.List;
import java.util.Set;

import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.rule.FormulaRule;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.rule.ParentCopyRule;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;


/**
 * Process Formulas and Parent Checks per Dependency Graph order.
 * <p>
 * 
 * Interweaving allows Parent-1 replicates to be used for Parent-2 FK,
 * and to compute FK (e.g., group by date using auto-insert parent)
 */
public class FormulasAndParentChecks extends LogicPhaseBase implements LogicPhase {
	
	/**
	 * execute aLogicObject annotations/methods represented in aFormulaAndParentRuleList.
	 * 
	 * <br><br>
	 * Command Pattern - instantiate / run logic phase
	 * 
	 * @param aLogicRunner
	 */
	public FormulasAndParentChecks(LogicRunner aLogicRunner) {	
		super(aLogicRunner);
	}
	
	@Override
	public void setLogicPhase() {
		logicPhase = LogicRunnerPhase.LOGIC; 
	}

	private static final LogicLogger _logger = LogicLogger.getLogger(LoggerName.RULES_ENGINE);
	private static final LogicLogger _logSys = LogicLogger.getLogger(LoggerName.SYSDEBUG);
	
	
	
	
	/**
	 * execute aLogicObject methods represented in aFormulaAndParentRuleList.
	 *
	 * this runs the formulas and parent checks
	 * 
	 * @param aLogicRunner
	 */
	@Override
	public void execute() {
		LogicGroup logicGroup = logicRunner.getLogicGroup();
		if (logicGroup == null) {
			if (_logger.isDebugEnabled()) _logger.debug("No logic found for class " + 
					logicRunner.getCurrentDomainObject().getEntityName() + ", moving on.");
			return;
		}
		
		// Check if any ParentCopy attributes need to be refreshed
		Set<ParentCopyRule> parentCopies = logicGroup.getParentCopies();
		for (ParentCopyRule parentCopy : parentCopies) {
			parentCopy.execute(logicRunner.getLogicObject(), logicRunner);
		}

		Object logicObject = logicRunner.getLogicObject();
		if (_logSys.isDebugEnabled())  
			_logSys.debug ("#execute formulas for ", logicRunner);

		List<FormulaRule> formulaAndParentRuleList = logicGroup.getFormulas();
		for (FormulaRule eachRule: formulaAndParentRuleList) {
			boolean didRuleRun = eachRule.execute(logicObject, logicRunner);
			if (didRuleRun) {
				// Do something here?
			}
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  FormulasAndParentChecks.java 983 2012-03-23 01:39:10Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 