package com.autobizlogic.abl.mgmt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.perf.PerformanceMonitor;
import com.autobizlogic.abl.perf.RuleStat;
import com.autobizlogic.abl.rule.*;

/**
 * The management service for performance data.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class PerformanceService {

	public static Map<String, Object> service(Map<String, String> args) {
		
		String serviceName = args.get("service");
		if (serviceName.equals("getAllStats"))
			return getAllStats(args);

		return null;
	}

	public static Map<String, Object> getAllStats(Map<String, String> args) {
		HashMap<String, Object> result = new HashMap<String, Object>();
		
		Map<String, Map<String, Map<String, Object>>> statsMap = new HashMap<String, Map<String, Map<String, Object>>>();
		Collection<RuleStat> allStats = PerformanceMonitor.getAllRuleStats();
		
		for (RuleStat stat : allStats) {
			String logicClassName = stat.getRule().getLogicGroup().getLogicClassName();
			Map<String, Map<String, Object>> classEntry = statsMap.get(logicClassName);
			
			if (classEntry == null) {
				classEntry = new HashMap<String, Map<String, Object>>();
				statsMap.put(logicClassName, classEntry);
			}
						
			Map<String, Object> statMap = new HashMap<String, Object>();
			statMap.put("firstExecutionTime", stat.getFirstExecutionTime());
			statMap.put("lastExecutionTime", stat.getLastExecutionTime());
			statMap.put("numberOfExecutions", stat.getNumberOfExecutions());
			statMap.put("totalExecutionTime", stat.getTotalExecutionTime());
			statMap.put("ruleType", getRuleType(stat.getRule()));
			classEntry.put(stat.getRule().getLogicMethodName(), statMap);
		}
		
		result.put("data", statsMap);
		return result;
	}
	
	private static String getRuleType(AbstractRule rule) {
		if (rule instanceof CommitActionRule)
			return "Commit action";
		else if (rule instanceof EarlyActionRule)
			return "Early action";
		else if (rule instanceof ActionRule)
			return "Action";
		else if (rule instanceof CommitConstraintRule)
			return "Commit constraint";
		else if (rule instanceof ConstraintRule)
			return "Constraint";
		else if (rule instanceof CountRule)
			return "Count";
		else if (rule instanceof SumRule)
			return "Sum";
		else if (rule instanceof FormulaRule)
			return "Formula";
		else if (rule instanceof ParentCopyRule)
			return "Parent copy";
		
		return "Unknown rule type : " + rule.getClass().getName();
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 