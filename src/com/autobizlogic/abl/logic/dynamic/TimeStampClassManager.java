package com.autobizlogic.abl.logic.dynamic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;


/**
 * Load class files from the file system, and reload them if the timestamp on the .class file
 * has changed.
 */
public class TimeStampClassManager implements LogicClassManager {
	
	private String path;
	private long lastCheckTimestamp = 0;
	private int checkMinInterval = 1000;
	private ClassLoader currentClassLoader;
	private Map<String, Long> timestamps = new HashMap<String, Long>();
	private Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
	// For this next one, we used to use System.getProperty("file.separator");
	// but that evaluates to a backslash on Windows, which confuses String.replaceAll.
	private static final String slash = "/";
	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.DEPENDENCY);

	/**
	 * Create an instance for the given class directory. Classes will be found starting from that
	 * directory.
	 */
	public TimeStampClassManager(String path) {
		this.path = path;
		if ( ! path.endsWith(slash))
			this.path += slash;
	}
	
	/**
	 * Constructor invoked by the engine when this class is specified in the config file.
	 * @param params Must contain a key "directory" pointing to a directory.
	 */
	public TimeStampClassManager(Map<String, String> params) {
		path = params.get("directory");
		if (path == null || path.trim().length() == 0)
			throw new RuntimeException("TimeStampClassManager requires a parameter named directory");
		path = path.trim();
		File dir = new File(path);
		if ( ! dir.exists())
			throw new RuntimeException("TimeStampClassManager's directory does not exist: " + path);
		if ( ! dir.isDirectory())
			throw new RuntimeException("TimeStampClassManager's directory is not a directory: " + path);
		if ( ! path.endsWith(slash))
			this.path += slash;
	}

	/**
	 * Returns the class with the given name from the directory structure, or null if such a class
	 * is not found.
	 */
	@Override
	public Class<?> getClassForName(String clsName) {
		
		String rawName = clsName.replaceAll("\\.", slash);
		String fullName = path + rawName + ".class";
		File classFile = new File(fullName);
		if ( ! classFile.exists()) {
			timestamps.remove(clsName);
			classes.remove(clsName);
			return null;
		}
		
		if (classes.get(clsName) != null && classFile.lastModified() == timestamps.get(clsName))
			return classes.get(clsName);
		
		if (classes.get(clsName) != null) {
			resetClassLoader();
			ClassLoaderManager.getInstance().forgetAllLogicClasses();
		}
		
		Class<?> cls;
		try {
			cls = getClassLoader().loadClass(clsName);
		}
		catch(Exception ex) {
			throw new RuntimeException("Unable to load class: " + clsName, ex);
		}
		
		long currentTimestamp = classFile.lastModified();
		timestamps.put(clsName, currentTimestamp);
		classes.put(clsName, cls);
		return cls;
	}
	
	@Override
	public byte[] getByteCodeForClass(String name) {

		String rawName = name.replaceAll("\\.", slash);
		String fullName = path + rawName + ".class";
		ByteArrayOutputStream baos = null;
		try {
			InputStream classStr = new FileInputStream(fullName);
			baos = new ByteArrayOutputStream();
			int b = classStr.read();
			while (b != -1) {
				baos.write(b);
				b = classStr.read();
			}
			baos.close();
			classStr.close();
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while reading byte code for " + name, ex);
		}
		return baos.toByteArray();
	}
	
	/**
	 * Determine whether any classes need reloading. If any does, then all classes are forgotten.
	 */
	@Override
	public boolean classesNeedsReloading() {
		
		if (System.currentTimeMillis() - lastCheckTimestamp < checkMinInterval)
			return false;
		
		lastCheckTimestamp = System.currentTimeMillis();

		for (String className : classes.keySet()) {
			String rawName = className.replaceAll("\\.", slash);
			String fullName = path + rawName + ".class";
			File classFile = new File(fullName);
			if ( ! classFile.exists()) {
				resetClassLoader();
				if (log.isDebugEnabled())
					log.debug("Logic class " + className + " has been deleted - reloading all logic classes");
				return true;
			}
			
			Long oldTimestamp = timestamps.get(className);
			if (oldTimestamp == null)
				oldTimestamp = 0L;
			
			long currentTimestamp = classFile.lastModified();
			if (currentTimestamp > oldTimestamp && oldTimestamp != 0) {
				forgetAllClasses();
				if (log.isDebugEnabled())
					log.debug("Logic class " + className + " has changed - reloading all logic classes");
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public ClassLoader getClassLoader() {
		if (currentClassLoader == null) {
			currentClassLoader = new TimeStampClassLoader(path, 
					ClassLoaderManager.getInstance().getAllClassLoader());
		}
		return currentClassLoader;
	}
	
	@Override
	public void forgetAllClasses() {
		timestamps = new HashMap<String, Long>();
		classes = new HashMap<String, Class<?>>();
		currentClassLoader = new TimeStampClassLoader(path, 
				ClassLoaderManager.getInstance().getAllClassLoader());
	}
	
	private void resetClassLoader() {
		timestamps = new HashMap<String, Long>();
		classes = new HashMap<String, Class<?>>();
		currentClassLoader = new TimeStampClassLoader(path, 
				ClassLoaderManager.getInstance().getAllClassLoader());
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
 