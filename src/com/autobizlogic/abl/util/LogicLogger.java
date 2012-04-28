package com.autobizlogic.abl.util;

import java.net.URL;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;

import com.autobizlogic.abl.engine.LogicRunner;

public class LogicLogger {

	/**
	 * The name of the configuration file
	 */
	private static final String CONFIG_FILE_NAME = "/ABL_logging.properties";
	private static final String CONFIG_FILE_NAME_DEFAULT = "/ABL_logging_default.properties";

	// Attempt to load the configuration file to let log4j initialize itself.
	static {
		try {
			URL configFileUrl = LogicLogger.class.getResource(CONFIG_FILE_NAME);
			if (configFileUrl == null) {
				System.err.println("com.autobizlogic.abl.rulesengine - LogicLogger unable to find ABL_logging.properties in class path, using system  /ABL_logging_default.properties \n\t\t\t\t\t\t\t(appropriate for development, should be configured for production)");
				PropertyConfigurator.configure(LogicLogger.class.getResource(CONFIG_FILE_NAME_DEFAULT));
			} else
				PropertyConfigurator.configure(configFileUrl);
		} catch(Exception ex) {
			System.err.println("ERROR : Unable to configure log4j: " + ex.getMessage() + ", using defaults");
		}
	}

	/**
	 * The different loggers for this system. Each logger represents a logical area of the system.
	 * Note that all loggers have a name that's exactly 10 characters long. This is critical so that
	 * the log will align correctly.
	 */
	public enum LoggerName {

		/**
		 * The logger for dependency analysis
		 */
		DEPENDENCY("abl.depend"),
		
		/**
		 * The logger for the event mechanism.
		 */
		EVENT_LISTENER("abl.evtlst"),
		
		/**
		 * The logger to use when nothing else really fits
		 */
		GENERAL("abl.generl"),
		
		/**
		 * The logger for persistence, e.g. reading and writing from Hibernate.
		 */
		PERSISTENCE("abl.persis"),
		
		/**
		 * The logger for BusLogicExtensions detail, e.g., attributes copied.
		 */
		BUSLOGICEXT("abl.buslog"),
		
		/**
		 * The logger used by the Recompute facility
		 */
		RECOMPUTE("abl.recomp"),
		
		/**
		 * The logger for all the business rules. This is usually the one most
		 * frequently turned up to follow logic execution.
		 */
		RULES_ENGINE("abl.engine"),
		
		/**
		 * The logger used for internal debugging messages. This is normally turned off,
		 * except for ABL developers.
		 */
		SYSDEBUG("abl.sysdbg");

		private String name;

		private LoggerName(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * The log4j <code>Logger</code> instance associated with this logger.
	 */
	private org.apache.log4j.Logger logger;  // Log4j Logger instance.

	///////////////////////////////////////////////////////////////////////////////////////////
	
	public LogicLogger( LoggerName logName ) {
		String name = logName.getName();
		//if (name.contains("debug")) 
		//	name += "\t       ";
		this.logger = org.apache.log4j.Logger.getLogger( name );
	}

	public boolean isDebugEnabled() {
		return logger.isDebugEnabled();
	}

	public boolean isInfoEnabled() {
		return logger.isInfoEnabled();
	}
	
	public boolean isWarnEnabled() {
		return logger.isEnabledFor(Level.WARN);
	}

	/**
	 * Get a logger from the set of known loggers.
	 */
	public static LogicLogger getLogger(LoggerName logName) {
		return new LogicLogger(logName);
	}

	/**
	 * Write a message with the <code>DEBUG</code> severity to the log.
	 * @param message	    The message to log.
	 */
	public final void debug( String message ) {
		this.logger.log(Level.DEBUG, message, null);
	}

	/**
	 * Write a message and exception with the <code>DEBUG</code> severity to the log.
	 *
	 * @param message	    The message to log.
	 * @param error		    The exception or error to log.
	 */
	public final void debug( String message, Throwable error ) {
		this.logger.log(Level.DEBUG, message, error);
	}

	/**
	 * Write a message and exception with the <code>DEBUG</code> severity to the log.
	 *
	 * @param message The message to log.
	 * @param aLogicRunner The LogicRunner currently operating
	 */
	public final void debug (String aMsg, LogicRunner aLogicRunner) {
		String msg = logicRunnerInfo(aMsg, aLogicRunner);
		this.logger.log(Level.DEBUG, msg, null);
	}

	/**
	 * Write a message and exception with the <code>INFO</code> severity to the log.
	 *
	 * @param message	    The message to log.
	 */
	public final void info(String msg) {
		logger.info(msg);
	}

	/**
	 * Write a message and exception with the <code>INFO</code> severity to the log.
	 *
	 * @param message The message to log.
	 * @param error The exception or error to log.
	 */
	public final void info( String msg, Throwable error) {
		logger.info(msg, error);
	}

	/**
	 * Write a message and exception with the <code>INFO</code> severity to the log.
	 *
	 * @param message The message to log.
	 * @param aLogicRunner The exception or error to log.
	 */
	public final void info( String aMsg, LogicRunner aLogicRunner) {
		String msg = logicRunnerInfo(aMsg, aLogicRunner);
		this.logger.info( msg);
	}

	/**
	 * Write a message and exception with the <code>WARN</code> severity to the log.
	 * @param msg The message to log.
	 */
	public final void warn( String msg ) {
		logger.warn(msg);
	}

	/**
	 * Write a message and exception with the <code>WARN</code> severity to the log.
	 * @param message	    The message to log.
	 * @param ex		    The exception or error to log.
	 */
	public final void warn( String msg, Throwable ex ) {
		logger.warn(msg, ex);
	}

	/**
	 * Write a message and exception with the <code>ERROR</code> severity to the log.
	 * @param message	    The message to log.
	 */
	public final void error( String msg ) {
		logger.error(msg);
	}

	/**
	 * Write a message and exception with the <code>ERROR</code> severity to the log.
	 * @param message	    The message to log.
	 * @param ex		    The exception or error to log.
	 */
	public final void error( String msg, Throwable ex ) {
		logger.error(msg, ex);
	}

	/**
	 * Write a message and exception with the <code>ERROR</code> severity to the log.
	 * @param message	    The message to log.
	 */
	public final void fatal(String msg) {
		logger.fatal(msg);
	}

	/**
	 * Write a message and exception with the <code>ERROR</code> severity to the log.
	 * @param message	    The message to log.
	 * @param ex		    The exception or error to log.
	 */
	public final void fatal(String msg, Throwable ex) {
		logger.fatal(msg, ex);
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////
	// Special logging with a LogicRunner

	/**
	 * 
	 * @param aMsg
	 * @param aLogicRunner
	 * @return entire string (e.g., for info/debug(aMsg, aLogicRunner)
	 */
	public static String logicRunnerInfo(String aMsg, LogicRunner aLogicRunner) {
		return aLogicRunner.toString(aMsg);
	}


	
	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  LogicLogger.java 951 2012-03-16 08:18:29Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 