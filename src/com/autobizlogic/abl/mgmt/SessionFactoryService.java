package com.autobizlogic.abl.mgmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Settings;
import org.hibernate.impl.SessionFactoryImpl;

import com.autobizlogic.abl.hibernate.HibernateConfiguration;

/**
 * The management service for session factory information.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class SessionFactoryService {
	
	public static Map<String, Object> service(Map<String, String> args) {
		
		String serviceName = args.get("service");
		if (serviceName.equals("getAllSessionFactories"))
			return getAllSessionFactories();
		if (serviceName.equals("getSessionFactoryDetails"))
			return getSessionFactoryDetails(args);

		return null;
	}
	
	public static Map<String, Object> getAllSessionFactories() {
		HashMap<String, Object> result = new HashMap<String, Object>();
		List<Map<String, Object>> sfs = new Vector<Map<String, Object>>();
		
		Set<SessionFactory> sessionFactories = HibernateConfiguration.getRegisteredSessionFactories();
		for (SessionFactory factory : sessionFactories) {
			Map<String, Object> handle = new HashMap<String, Object>();
			handle.put("sessionFactoryId", "" + factory.hashCode());
			handle.put("name", "Session factory " + factory.hashCode());
			sfs.add(handle);
		}
		
		result.put("data", sfs);
		
		return result;
	}

	public static Map<String, Object> getSessionFactoryDetails(Map<String, String> args) {
		String sessionFactoryId = args.get("sessionFactoryId");
		HashMap<String, Object> result = new HashMap<String, Object>();
		SessionFactory sessionFactory = HibernateConfiguration.getSessionFactoryById(sessionFactoryId);
		if (sessionFactory == null)
			return null;
		
		HashMap<String, Object> data = new HashMap<String, Object>();

		Properties props = ((SessionFactoryImpl)sessionFactory).getProperties();
		data.put("properties", props);
		
		Map<String, String> settings = new HashMap<String, String>();
		Settings sets = ((SessionFactoryImpl)sessionFactory).getSettings();
		settings.put("Batcher factory", sets.getBatcherFactory() == null ? "none" : sets.getBatcherFactory().toString());
		settings.put("Default catalog name", sets.getDefaultCatalogName());
		settings.put("Default schema name", sets.getDefaultSchemaName());
		settings.put("Session factory name", sets.getSessionFactoryName());
		settings.put("Cache region prefix", sets.getCacheRegionPrefix());
		settings.put("Connection provider", sets.getConnectionProvider() == null ? "none" : sets.getConnectionProvider().toString());
		settings.put("Connection release mode", sets.getConnectionReleaseMode() == null ? "none" : sets.getConnectionReleaseMode().toString());
		settings.put("Default batch fetch size", "" + sets.getDefaultBatchFetchSize());
		settings.put("JDBC batch size", "" + sets.getJdbcBatchSize());
		settings.put("Maximum fetch depth", sets.getMaximumFetchDepth() == null ? "none" : sets.getMaximumFetchDepth().toString());
		settings.put("Default entity mode", sets.getDefaultEntityMode() == null ? "none" : sets.getDefaultEntityMode().toString());
		settings.put("Dialect", sets.getDialect() == null ? "none" : sets.getDialect().toString());
		settings.put("Entity tuplizer factory", sets.getEntityTuplizerFactory() == null ? "none" : sets.getEntityTuplizerFactory().toString());
		settings.put("JDBC fetch size", sets.getJdbcFetchSize() == null ? "none" : sets.getJdbcFetchSize().toString());
		settings.put("JDBC support", sets.getJdbcSupport() == null ? "none" : sets.getJdbcSupport().toString());
		settings.put("Query cache factory", sets.getQueryCacheFactory() == null ? "none" : sets.getQueryCacheFactory().toString());
		settings.put("Query substitutions", sets.getQuerySubstitutions() == null ? "none" : sets.getQuerySubstitutions().toString());
		settings.put("Query translator factory", sets.getQueryTranslatorFactory() == null ? "none" : sets.getQueryTranslatorFactory().toString());
		settings.put("Region factory", sets.getRegionFactory() == null ? "none" : sets.getRegionFactory().toString());
		settings.put("SQL exception converter", sets.getSQLExceptionConverter() == null ? "none" : sets.getSQLExceptionConverter().toString());
		settings.put("SQL statement logger", sets.getSqlStatementLogger() == null ? "none" : sets.getSqlStatementLogger().toString());
		settings.put("Transaction factory", sets.getTransactionFactory() == null ? "none" : sets.getTransactionFactory().toString());
		settings.put("Transaction manager lookup", sets.getTransactionManagerLookup() == null ? "none" : sets.getTransactionManagerLookup().toString());
		data.put("settings", settings);
		
		data.put("importFiles", ((SessionFactoryImpl)sessionFactory).getSettings().getImportFiles());
		
		result.put("data", data);
		return result;

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
 