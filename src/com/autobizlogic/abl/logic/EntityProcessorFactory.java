package com.autobizlogic.abl.logic;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;
import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;

/**
 * The class that instantiates EntityProcessor if specified in the config file.
 */
public class EntityProcessorFactory {
	
	private static boolean configChecked = false;
	private static Class<?> processorClass;

	public static void preProcess(Verb verb, PersistentBean bean) {
		EntityProcessor processor = getEntityProcessor();
		if (processor == null)
			return;
		processor.preProcess(verb, bean);
	}
	
	public static void postProcess(Verb verb, PersistentBean bean) {
		EntityProcessor processor = getEntityProcessor();
		if (processor == null)
			return;
		processor.postProcess(verb, bean);
	}

	private static EntityProcessor getEntityProcessor() {
		
		synchronized(EntityProcessorFactory.class) {
			if ( ! configChecked) {
				String clsName = LogicConfiguration.getInstance().getProperty(PropertyName.ENTITY_PROCESSOR);
				configChecked = true;
				if (clsName == null || clsName.trim().length() == 0) {
					return null;
				}
				try {
					processorClass = ClassLoaderManager.getInstance().getClassFromName(clsName);
				}
				catch(Exception ex) {
					throw new RuntimeException("Unable to load class specified in ABL configuration " +
							"for " + LogicConfiguration.PropertyName.ENTITY_PROCESSOR, ex);
				}
			}
		}
		if (processorClass == null)
			return null;
		
		try {
			return (EntityProcessor)processorClass.newInstance();
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while instantiating the class specified in " +
					"the ABL configuration for " + LogicConfiguration.PropertyName.ENTITY_PROCESSOR, ex);
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
 