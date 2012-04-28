package com.autobizlogic.abl.perf;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.rule.AbstractRule;

public class PerformanceMonitor {
	
	private static final Map<String, RuleStat> stats = new HashMap<String, RuleStat>();

	/**
	 * Signal the PerformanceMonitor that a rule has just executed, and tell it how long it took.
	 */
	public static void addRuleExecution(AbstractRule rule, long executionTime ) {
		
		String fullName = buildKeyForRule(rule);
		
		RuleStat ruleStat = null;
		synchronized(stats) {
			ruleStat = stats.get(fullName);
			if (ruleStat == null) {
				ruleStat = new RuleStat(rule);
				stats.put(fullName, ruleStat);
			}
		}
		ruleStat.addExecutionTime(executionTime);
	}
	
	public static RuleStat getStatsForRule(AbstractRule rule) {
		String fullName = buildKeyForRule(rule);
		return stats.get(fullName);
	}
	
	public static RuleStat getStatsForRule(String fullName) {
		return stats.get(fullName);
	}
	
	public static Collection<RuleStat> getAllRuleStats() {
		return stats.values();
	}
	
	private static String buildKeyForRule(AbstractRule rule) {
		String logicGroupName = rule.getLogicGroup().getLogicClassName();
		String ruleName = rule.getLogicMethodName();
		return logicGroupName + "/" + ruleName;
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
 