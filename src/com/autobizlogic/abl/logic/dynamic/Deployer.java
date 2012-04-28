package com.autobizlogic.abl.logic.dynamic;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Blob;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import org.apache.log4j.Level;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.classic.Session;

import com.autobizlogic.abl.logic.dynamic.database.*;

/**
 * Deploy a logic jar to a database for use by DatabaseClassManager.
 */
public class Deployer {
	
	/**
	 * The path to the Hibernate config file
	 */
	public static final String HIB_CFG_FILE = "hibCfgFile";

	/**
	 * A ready-to-use Hibernate Configuration object
	 */
	public static final String HIB_CFG = "hibCfg";

	/**
	 * Parameter name for the name of the project into which to load the jar file. If the project
	 * does not exist, it is created.
	 */
	public static final String PROJECT_NAME = "projectName";
	
	/**
	 * The name of the jar file to load into the database, e.g. /home/jdoe/logic.jar
	 */
	public static final String JAR_FILE_NAME = "jarFileName";
	
	/**
	 * The date and time at which the given jar file should take effect. If not specified,
	 * the default is now.
	 */
	public static final String EFFECTIVE_DATE = "effectiveDate";
	
	/**************************************************************************************
	 * Load the given jar file into the given database.
	 */
	public static void main(String[] args) {
		
		if (args.length != 3 && args.length != 4) {
			printUsage("Invalid number of parameters");
			return;
		}
		
		// Read arguments
		String effectiveDateStr = null;
		Properties props = new Properties();
		props.put(HIB_CFG_FILE, args[0].trim());
		props.put(PROJECT_NAME, args[1].trim());
		props.put(JAR_FILE_NAME, args[2].trim());
		if (args.length == 4)
			effectiveDateStr = args[3].trim();
		
		if (effectiveDateStr == null)
			props.put(EFFECTIVE_DATE, new Date());
		else {
			Date d = parseDate(effectiveDateStr);
			if (d == null) {
				printUsage("Invalid date format - should be e.g. 2012-01-13-16:30");
				return;
			}
			props.put(EFFECTIVE_DATE, d);
		}
		
		// Validate arguments
		if (props.get(HIB_CFG_FILE).equals("")) {
			printUsage("Hibernate configuration file must be specified");
			return;
		}
		File cfgFile = new File((String)props.get(HIB_CFG_FILE));
		if ( ! cfgFile.exists()) {
			printUsage("Hibernate configuration file " + props.get(HIB_CFG_FILE) + " could not be found");
			return;
		}
		
		if (props.get(PROJECT_NAME).equals("")) {
			printUsage("Project name must be specified");
			return;
		}
		
		if (props.get(JAR_FILE_NAME).equals("")) {
			printUsage("Jar file name must be specified");
			return;
		}
		File jarFile = new File((String)props.get(JAR_FILE_NAME));
		if ( ! jarFile.exists()) {
			printUsage("Jar file " + props.get(JAR_FILE_NAME) + " could not be found");
			return;
		}
		
		deploy(props);
	}
	
	/**
	 * Deploy a jar into a database for use by DatabaseClassManager.
	 * @param props Should contain the required parameters:
	 * <ul>
	 * <li>either HIB_CFG_FILE (Hibernate config file path as a string) or 
	 * HIB_CFG as a Hibernate Configuration object
	 * <li>PROJECT_NAME: the name of the project to deploy to (will be created if it does not exist)
	 * <li>JAR_FILE_NAME: the path of the jar file to deploy, as a String
	 * <li>EFFECTIVE_DATE: the date/time at which the new logic classes should take effect, 
	 * as a java.util.Date (optional)
	 * </ul>
	 * @return Null if everything went OK, otherwise a reason for the failure
	 */
	public static String deploy(Properties props) {
		
		Logger root = Logger.getRootLogger();
		root.addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN)));
		Logger.getLogger("org.hibernate").setLevel(Level.WARN);
		Logger.getLogger("org.hibernate.tool.hbm2ddl").setLevel(Level.DEBUG);
		Logger.getLogger("org.hibernate.SQL").setLevel(Level.DEBUG);
		Logger.getLogger("org.hibernate.transaction").setLevel(Level.DEBUG);
		
		Configuration config;
		if (props.get(HIB_CFG) == null) {
			config = new Configuration();
			File cfgFile = new File((String)props.get(HIB_CFG_FILE));
			config.configure(cfgFile);
		}
		else
			config = (Configuration)props.get(HIB_CFG);
		
		if (config.getClassMapping(Project.class.getName()) == null) {
			config.addAnnotatedClass(Project.class);
			config.addAnnotatedClass(LogicFile.class);
			config.addAnnotatedClass(LogicFileLog.class);
		}
				
		SessionFactory sessFact = config.buildSessionFactory();
		Session session = sessFact.getCurrentSession();
		Transaction tx = session.beginTransaction();
		
		Query query = session.createQuery("from Project where name = :name").setString("name", 
				(String)props.get(PROJECT_NAME));
		Project project = (Project)query.uniqueResult();
		if (project == null) {
			project = new Project();
			project.setName((String)props.get(PROJECT_NAME));
			session.save(project);
		}
		
		LogicFile logicFile = new LogicFile();
		String fileName = (String)props.get(JAR_FILE_NAME);
		String shortFileName = fileName;
		if (fileName.length() > 300)
			shortFileName = fileName.substring(0, 300);
		logicFile.setName(shortFileName);
		logicFile.setCreationDate(new Timestamp(System.currentTimeMillis()));
		File jarFile = new File((String)props.get(JAR_FILE_NAME));
		try {
			FileInputStream inStr = new FileInputStream(fileName);
			Blob blob = session.getLobHelper().createBlob(inStr, jarFile.length());
			logicFile.setContent(blob);
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while storing jar file into database", ex);
		}
		Date effDate = (Date)props.get(EFFECTIVE_DATE);
		logicFile.setEffectiveDate(new Timestamp(effDate.getTime()));
		logicFile.setProject(project);
		session.save(logicFile);
		
		tx.commit();
		sessFact.close();

		return null;
	}
	
	private static void printUsage(String extraMsg) {
		if (extraMsg != null)
			System.err.println("Invalid usage: " + extraMsg);
		System.err.println("Usage: java com.autobizlogic.abl.logic.dynamic.Deployer ");
		System.err.println("     <hibernate-config-file> <project-name> <jar-file> <effective-date>");
	}
	
	private static Date parseDate(String dateStr) {
		Date date = null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH:mm");
		try {
			date = sdf.parse(dateStr);
		}
		catch(Exception ex) {
			return null;
		}
		return date;
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
 