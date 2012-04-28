package com.autobizlogic.abl.logic;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.engine.LogicException;

/**
 * The class responsible for creating the sole instance of BusinessLogicFactory.
 */
public class BusinessLogicFactoryManager {

	/**
	 * Create singleton of BusinessLogicFactory - either default, or name supplied in properties
	 */
	public static BusinessLogicFactory getBusinessLogicFactory() {
		
		// 
		String clsName = LogicConfiguration.getInstance().
				getProperty(LogicConfiguration.PropertyName.BUSINESS_LOGIC_FACTORY);
		// Note that clsName should never be null because it's defaulted in LogicConfiguration,
		// but you never know...
		if (clsName == null || clsName.trim().length() == 0)
			clsName = LogicConfiguration.PropertyName.BUSINESS_LOGIC_FACTORY.getDefaultValue();
		
		Class<?> busLogicFactoryClass = null;
		try {
			busLogicFactoryClass = Class.forName(clsName);
		} catch (ClassNotFoundException e) {
			throw new LogicException("Cannot find BusinessLogicFactoryClass: " + clsName, e);
		}
		try {
			return (BusinessLogicFactory) busLogicFactoryClass.newInstance();
		} catch (Exception e) {
			throw new LogicException("Cannot instantiate BusinessLogicFactoryClass: " + clsName, e);
		}
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
 