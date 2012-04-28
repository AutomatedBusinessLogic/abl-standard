package com.autobizlogic.abl.logic.dynamic;

import java.io.File;
import java.net.InetAddress;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;
import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.logic.dynamic.database.LogicFile;
import com.autobizlogic.abl.logic.dynamic.database.LogicFileLog;
import com.autobizlogic.abl.logic.dynamic.database.Project;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * Logic class manager that loads classes from a jar stored ina relational database.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class DatabaseClassManager implements LogicClassManager {

	private Configuration cfg;
	private long lastCheckTimestamp = 0;
	private int checkMinInterval = 600000;
	private long lastLogicTimestamp = 0;
	private String projectName;
	private String persistenceUnitName;
	private DatabaseClassLoader currentClassLoader = null;
	private LogicLogger log = LogicLogger.getLogger(LoggerName.DEPENDENCY);
	
	public DatabaseClassManager(Configuration cfg, String projectName) {
		this.cfg = cfg;
		this.projectName = projectName;
		String intervalStr = LogicConfiguration.getInstance().getProperty(PropertyName.DATABASE_LOGIC_REFRESH_INTERVAL);
		if (intervalStr != null) {
			try {
				checkMinInterval = Integer.valueOf(intervalStr) * 1000;
			}
			catch(Exception ex) {
				throw new RuntimeException("ABL configuration parameter " + 
						PropertyName.DATABASE_LOGIC_REFRESH_INTERVAL.getName() + " has an invalid value.");
			}
		}
		checkForUpdate();
	}
	
	public DatabaseClassManager(Map<String, String> params) {

		projectName = params.get("project_name");
		if (projectName == null || projectName.trim().length() == 0)
			throw new RuntimeException("ABL configuration file specifies a DatabaseClassManager, " +
					"but does not specify a value for project_name");
		
		String checkMinIntervalStr = params.get("check_min_interval");
		if (checkMinIntervalStr != null && checkMinIntervalStr.trim().length() > 0) {
			try {
				checkMinInterval = Integer.valueOf(checkMinIntervalStr) * 1000;
			}
			catch(Exception ex) {
				log.error("ABL Configuration error: logic class manager (database) has parameter check_min_interval " +
						"set to invalid value. Valid values are 0 - 2147483648. The default (600 seconds) will be used.");
				checkMinInterval = 600000;
			}
		}

		String persUnit = params.get("persistence_unit");
		if (persUnit != null && persUnit.trim().length() > 0) {
			persistenceUnitName = persUnit.trim();
		}
		else {
			String cfgFilePath = params.get("hibernate_cfg");
			File cfgFile = new File(cfgFilePath);
			if ( ! cfgFile.exists())
				throw new RuntimeException("ABL configuration specifies " + cfgFilePath +
						" as the Hibernate config file for dynamic logic classes, but that file cannot be found.");
			cfg = new Configuration();
			cfg.configure(cfgFile);
			if (cfg.getClassMapping(Project.class.getName()) == null) {
				cfg.addAnnotatedClass(Project.class);
				cfg.addAnnotatedClass(LogicFile.class);
				cfg.addAnnotatedClass(LogicFileLog.class);
			}
		}
		checkForUpdate();
	}
	
	@Override
	public Class<?> getClassForName(String name) {
		
		if (currentClassLoader == null)
			return null;
		
		Class<?> cls = null;
		try {
			cls = currentClassLoader.loadClass(name);
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while trying to load logic class " + name +
					" from database", ex);
		}
		
		return cls;
	}
	
	@Override
	public byte[] getByteCodeForClass(String name) {
		return currentClassLoader.getClassBytes(name);
	}

	@Override
	public boolean classesNeedsReloading() {
		
		if (System.currentTimeMillis() - lastCheckTimestamp < checkMinInterval)
			return false;
		
		lastCheckTimestamp = System.currentTimeMillis();
		return checkForUpdate();
	}

	@Override
	public ClassLoader getClassLoader() {
		return currentClassLoader;
	}
	
	@Override
	public void forgetAllClasses() {
		if (currentClassLoader == null)
			return;

		currentClassLoader.forgetAllClasses();
		checkForUpdate();
	}
	
	private boolean checkForUpdate() {
		if (persistenceUnitName != null)
			return checkForUpdateJPA();
		return checkForUpdateHibernate();
	}

	private boolean checkForUpdateHibernate() {
		try {
			SessionFactory sessFact = cfg.buildSessionFactory();
			Session session = sessFact.getCurrentSession();
			Transaction tx = session.beginTransaction();
			
			Query query = session.createQuery("from Project where name = :name").setString("name", 
					projectName);
			Project project = (Project)query.uniqueResult();
			
			LogicFileLog fileLog = loadClassesFromProject(project);
			if (fileLog == null) {
				tx.commit();
				return false;
			}
			session.save(fileLog);
			
			tx.commit();
			return true;
		}
		catch(Exception ex) {
			log.error("Unable to check for logic update", ex);
			return false;
		}
	}
	
	private boolean checkForUpdateJPA() {
		try {
			EntityManagerFactory emf = Persistence.createEntityManagerFactory(persistenceUnitName);
			EntityManager em = emf.createEntityManager();
			EntityTransaction tx = em.getTransaction();
			tx.begin();
			TypedQuery<Project> query = em.createQuery("from Project where name = :name", Project.class);
			query.setParameter("name", projectName);
			Project project = query.getSingleResult();
			if (project == null)
				throw new RuntimeException("DatabaseClassManager: no such Project: " + projectName);
			LogicFileLog fileLog = loadClassesFromProject(project);
			if (fileLog == null) {
				tx.commit();
				return false;
			}
			em.persist(fileLog);
			tx.commit();
			return true;
		}
		catch(Exception ex) {
			log.error("Unable to check for logic update", ex);
			return false;
		}
	}
	
	private LogicFileLog loadClassesFromProject(Project project) {
		if (project == null) {
			throw new RuntimeException("Unable to check for logic update - project " + projectName + " does not exist.");
		}
		List<LogicFile> logicFiles = project.getLogicFiles();
		Timestamp now = new Timestamp(System.currentTimeMillis());
		LogicFile currentLogicFile = null;
		for (LogicFile logicFile : logicFiles) {
			if (logicFile.getEffectiveDate().after(now))
				continue;
			currentLogicFile = logicFile;
			break;
		}
		if (currentLogicFile == null) {
			throw new RuntimeException("Unable to find current logic file for project " + 
					projectName + " in database");
		}
		if (currentLogicFile.getEffectiveDate().getTime() <= lastLogicTimestamp) {
			if (log.isDebugEnabled())
				log.debug("Logic file has not changed in database, and therefore was not reloaded.");
			return null;
		}
		
		// We now have an updated logic file -- read all entries
		lastLogicTimestamp = currentLogicFile.getEffectiveDate().getTime();
		if (log.isDebugEnabled())
			log.debug("Reading updated logic file from database - timestamp is " + currentLogicFile.getCreationDate());
		Blob blob = currentLogicFile.getContent();
		currentClassLoader = new DatabaseClassLoader(blob, 
				ClassLoaderManager.getInstance().getAllClassLoader());
		
		// And make a log entry
		LogicFileLog fileLog = new LogicFileLog();
		
		String clientName = "Unknown client";
		try {
		    InetAddress addr = InetAddress.getLocalHost();
		    String hostname = addr.getHostName();
		    clientName = hostname + "(" + addr.getHostAddress() + ")";
		} catch (Exception ex) {
			clientName = "Unable to determine client name";
		}
		
		fileLog.setClientName(clientName);
		fileLog.setClientStatus("Logic updated");
		fileLog.setLogDate(new Timestamp(System.currentTimeMillis()));
		fileLog.setLogicFile(currentLogicFile);
		return fileLog;
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
 