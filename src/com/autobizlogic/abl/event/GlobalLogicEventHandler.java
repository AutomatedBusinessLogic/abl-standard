package com.autobizlogic.abl.event;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.event.impl.LogicListenerHandlerImpl;
import com.autobizlogic.abl.event.impl.TransactionSummaryListenerHandlerImpl;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;

/**
 * Use this class to retrieve the global logic event listener handler, ie the object
 * used to register logic event listeners.
 */
public class GlobalLogicEventHandler {
	
	private static LogicListenerHandler globalListenerHandler = new LogicListenerHandlerImpl();
	
	private static TransactionSummaryListenerHandler globalTransactionSummaryListenerHandler = new TransactionSummaryListenerHandlerImpl();

	static {
		// If the configuration has one or more class names, instantiate them
		String classNames = LogicConfiguration.getInstance().getProperty(LogicConfiguration.PropertyName.GLOBAL_EVENT_LISTENERS);
		if (classNames != null && classNames.trim().length() > 0) {
			classNames = classNames.trim();
			classNames = classNames.replaceAll(",", " ");
			String[] classes = classNames.split(" ");
			for (String clsName : classes) {
				LogicListener listener = null;
				clsName = clsName.trim();
				if (clsName.length() == 0)
					continue;
				try {
					Class<?> cls = ClassLoaderManager.getInstance().getClassFromName(clsName);
					Object instance = cls.newInstance();
					listener = (LogicListener)instance;
				}
				catch(Exception ex) {
					throw new RuntimeException("Error while attempting to add global event listener from configuration file. " +
							"The listener class name was: " + clsName, ex);
				}
				getGlobalLogicListenerHandler().addLogicListener(listener);
			}
		}
		
		// Same thing for transaction summary listeners
		classNames = LogicConfiguration.getInstance().getProperty(LogicConfiguration.PropertyName.GLOBAL_TRANSACTION_SUMMARY_LISTENERS);
		if (classNames != null && classNames.trim().length() > 0) {
			classNames = classNames.trim();
			classNames = classNames.replaceAll(",", " ");
			String[] classes = classNames.split(" ");
			for (String clsName : classes) {
				TransactionSummaryListener listener = null;
				clsName = clsName.trim();
				if (clsName.length() == 0)
					continue;
				try {
					Class<?> cls = ClassLoaderManager.getInstance().getClassFromName(clsName);
					Object instance = cls.newInstance();
					listener = (TransactionSummaryListener)instance;
				}
				catch(Exception ex) {
					throw new RuntimeException("Error while attempting to add global event listener from configuration file. " +
							"The listener class name was: " + clsName, ex);
				}
				getGlobalTransactionSummaryListenerHandler().addListener(listener);
			}
		}
	}

	/**
	 * Get the global logic event listener handler.
	 */
	public static LogicListenerHandler getGlobalLogicListenerHandler() {
		return globalListenerHandler;
	}
	
	public static TransactionSummaryListenerHandler getGlobalTransactionSummaryListenerHandler() {
		return globalTransactionSummaryListenerHandler;
	}
	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  GlobalLogicEventHandler.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 