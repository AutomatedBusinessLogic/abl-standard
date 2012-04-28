package com.autobizlogic.abl.engine.phase;

import com.autobizlogic.abl.logic.Verb;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.engine.LogicRunner;
import com.autobizlogic.abl.engine.LogicRunner.LogicRunnerPhase;
import com.autobizlogic.abl.rule.AbstractAggregateRule;
import com.autobizlogic.abl.rule.LogicGroup;
import com.autobizlogic.abl.rule.MinMaxRule;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;
import com.autobizlogic.abl.util.NumberUtil;

public class DefaultsAndAlterability extends LogicPhaseBase implements LogicPhase {
	
	public DefaultsAndAlterability(LogicRunner aLogicRunner) {
		super(aLogicRunner);
	}
	
	@Override
	public void setLogicPhase() {
		logicRunner.logicPhase = LogicRunnerPhase.LOGIC;
	}

	@SuppressWarnings("unused")
	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.RULES_ENGINE);

	/**
	 * Check aggregates null on insert, unaltered on update.
	 * Action is to ignore or raise exception, 
	 * per aggregateDefaultOverride property, as explained in properties file.
	 * 
	 * @param aLogicRunner
	 */
	@Override
	public void execute() {
		if (logicRunner.getVerb() == Verb.INSERT) 
			performInsertDefaults();
		else if (logicRunner.getVerb() == Verb.UPDATE) 
			performAlterabilityChecks();
	}

	/**
	 * For each aggregate, we initialize the value to zero <b>if</b> it's currently null.
	 * If it is not null, we leave it alone, because it may have been already set by a
	 * child logic runner.
	 */
	private void performInsertDefaults() {
		LogicGroup logicGroup = logicRunner.getLogicGroup();
		if (logicGroup != null) {
			for (AbstractAggregateRule eachAggregateRule: logicGroup.getAggregates()) {
				String attrName = eachAggregateRule.getBeanAttributeName();
				if (eachAggregateRule instanceof MinMaxRule) {// Do not initialize min/max -- null is meaningful
					logicRunner.getCurrentDomainObject().put(attrName, null);
				}
				else {
					zeroValue(logicRunner, attrName);
				}
			}
		}
	}

	private  void performAlterabilityChecks() {
		LogicGroup logicGroup = logicRunner.getLogicGroup();
		if (logicGroup != null) {
//			String defaultSettings= 
//				LogicConfiguration.getInstance().getProperty(LogicConfiguration.PropertyName.AGGREGRATE_DEFAULT_OVERRIDE);
//			boolean isSetDefaults = true;
//			if (defaultSettings != null && (defaultSettings.startsWith("n") || defaultSettings.startsWith("N")) )
//				isSetDefaults = false;  // means throw excp
//			PersistentBean currentDomainObject = aLogicRunner.getCurrentDomainObject();
//			PersistentBean priorDomainObject = aLogicRunner.getPriorDomainObject();
//			for (AbstractAggregateRule eachAggregateRule: logicGroup.getAggregates()) {
//				String attrName = eachAggregateRule.getBeanAttributeName();
//				Object aggregateValue = currentDomainObject.get(attrName);
//				Object priorAggregateValue = priorDomainObject.get(attrName);
//				if ( ! aggregateValue.equals(priorAggregateValue)) {
//					if (isSetDefaults) 
//						currentDomainObject.put(attrName, priorAggregateValue);
//					else
//						throw new LogicException("Aggregate value is not alterable, for attribute:" + attrName);
//				}
//			}
		}
	}

	/**
	 * Set the specified property to zero, regardless of its (numeric) type.
	 */
	private static void zeroValue(LogicRunner aLogicRunner, String aPropertyName) {
		PersistentBean currentDomainObject = aLogicRunner.getCurrentDomainObject();
		Class<?> attType = currentDomainObject.getMetaEntity().getMetaAttribute(aPropertyName).getType();
		Object zero = NumberUtil.convertNumberToType(0, attType);
		currentDomainObject.put(aPropertyName, zero);
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  DefaultsAndAlterability.java 1252 2012-04-24 06:23:09Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 