package com.autobizlogic.abl.perf;

import com.autobizlogic.abl.rule.AbstractRule;

/**
 * Statistics gathered for a specific business rule over the lifetime of a JVM.
 */
public class RuleStat {
	private AbstractRule rule;
	private long totalExecutionTime;
	private long numberOfExecutions;
	private long firstExecutionTime;
	private long lastExecutionTime;
	
	public RuleStat(AbstractRule rule) {
		this.rule = rule;
	}
	
	public synchronized void addExecutionTime(long execTime) {
		long now = System.currentTimeMillis();
		if (firstExecutionTime == 0)
			firstExecutionTime = now;
		lastExecutionTime = now;
		numberOfExecutions++;
		totalExecutionTime += execTime;
	}

	/**
	 * The total execution time for this business rule, in nanoseconds.
	 * @return
	 */
	public long getTotalExecutionTime() {
		return totalExecutionTime;
	}

	/**
	 * The number of times this rule has been executed.
	 * @return
	 */
	public long getNumberOfExecutions() {
		return numberOfExecutions;
	}

	/**
	 * The timestamp for when this rule was first executed.
	 */
	public long getFirstExecutionTime() {
		return firstExecutionTime;
	}

	/**
	 * The timestamp for when this rule was most recently executed.
	 */
	public long getLastExecutionTime() {
		return lastExecutionTime;
	}
	
	/**
	 * Get the rule for which this is a stat.
	 */
	public AbstractRule getRule() {
		return rule;
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
 