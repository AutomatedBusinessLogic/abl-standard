package com.autobizlogic.abl.logic.dynamic;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.autobizlogic.abl.logic.analysis.ClassLoaderManager;
import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * The class responsible for handling class loading and reloading from a given jar file.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class JarClassManager implements LogicClassManager {

	private File jarFile;
	private long lastCheckTimestamp = 0;
	private int checkMinInterval = 5000;
	private long lastTimestamp = 0;
	private JarClassLoader currentClassLoader;
	private Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.DEPENDENCY);

	/**
	 * Create an instance for the given jar file. This is used for testing only.
	 */
	public JarClassManager(String jarPath) {
		jarFile = new File(jarPath);
		if ( ! jarFile.exists())
			throw new RuntimeException("Unable to find jar file: " + jarPath);
		if ( ! jarFile.canRead())
			throw new RuntimeException("Unable to read jar file: " + jarPath);
		classesNeedsReloading();
	}
	
	/**
	 * Normal constructor, invoked by ABL
	 * @param params Should contain a value for "jar_path"
	 */
	public JarClassManager(Map<String, String> params) {
		String jarPath = params.get("jar_path");
		jarFile = new File(jarPath);
		if ( ! jarFile.exists())
			throw new RuntimeException("Unable to find jar file: " + jarPath);
		if ( ! jarFile.canRead())
			throw new RuntimeException("Unable to read jar file: " + jarPath);
		classesNeedsReloading();
	}
	
	/**
	 * Get the class with the given name from the jar. Returns null if the class does not
	 * exist in the jar.
	 */
	@Override
	public Class<?> getClassForName(String clsName) {
		
		long ts = jarFile.lastModified();
		if (ts > lastTimestamp) {
			forgetAllClasses();
			ClassLoaderManager.getInstance().forgetAllLogicClasses();
			lastTimestamp = jarFile.lastModified();
		}

		if (classes.containsKey(clsName)) {
			return classes.get(clsName);
		}
		
		try {
			Class<?> cls = currentClassLoader.loadClass(clsName);
			if (cls == null && log.isDebugEnabled()) {
				log.debug("Did not find class " + clsName + " in jar " + jarFile.getAbsolutePath());
			}
			classes.put(clsName, cls);
			return cls;
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while loading class: " + clsName + 
					" from jar " + jarFile.getAbsolutePath(), ex);
		}
	}
	
	@Override
	public byte[] getByteCodeForClass(String name) {
		return currentClassLoader.getClassBytes(name);
	}
	
	/**
	 * Return true if the jar file has been updated. If so, all classes are forgotten.
	 */
	@Override
	public boolean classesNeedsReloading() {
		
		if (System.currentTimeMillis() - lastCheckTimestamp < checkMinInterval) {
			log.debug("JarClassManager.classesNeedsReloading: skipping check, not enough time since last check");
			return false;
		}
		
		lastCheckTimestamp = System.currentTimeMillis();

		File currentFile = new File(jarFile.getAbsolutePath());
		boolean needsReloading = currentFile.lastModified() > lastTimestamp;
		if (needsReloading) {
			lastTimestamp = currentFile.lastModified();
			classes = new HashMap<String, Class<?>>();
			currentClassLoader = new JarClassLoader(jarFile.getAbsolutePath(), 
					ClassLoaderManager.getInstance().getAllClassLoader());
		}
		
		return needsReloading;
	}
	
	@Override
	public ClassLoader getClassLoader() {
		return currentClassLoader;
	}
	
	@Override
	public void forgetAllClasses() {
		lastTimestamp = 0;
		classes = new HashMap<String, Class<?>>();
		currentClassLoader = new JarClassLoader(jarFile.getAbsolutePath(), 
				ClassLoaderManager.getInstance().getAllClassLoader());
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
 