package com.autobizlogic.abl.logic.dynamic;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import com.autobizlogic.abl.util.LogicLogger;
import com.autobizlogic.abl.util.LogicLogger.LoggerName;

/**
 * Load logic classes from a database.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class DatabaseClassLoader extends ClassLoader {

	private Map<String, byte[]> classBytes = new HashMap<String, byte[]>();
	private Map<String, Class<?>> classes = new HashMap<String, Class<?>>();
	private ClassLoader parentClassLoader;
	private static final LogicLogger log = LogicLogger.getLogger(LoggerName.DEPENDENCY);

	/**
	 * Create a class loader from the given blob, which is assumed to contain a jar file.
	 */
	public DatabaseClassLoader(Blob blob, ClassLoader parentClassLoader) {
		
		this.parentClassLoader = parentClassLoader;
		
		try {
			InputStream blobStr = blob.getBinaryStream();
			JarInputStream jarStr = new JarInputStream(blobStr);
			JarEntry entry = jarStr.getNextJarEntry();
			while (entry != null) {
				String rawName = entry.getName();
				if ( ! rawName.endsWith(".class")) {
					entry = jarStr.getNextJarEntry();
					continue;
				}
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				int b = jarStr.read();
				while (b != -1) {
					baos.write(b);
					b = jarStr.read();
				}
				classBytes.put(entry.getName(), baos.toByteArray());
				entry = jarStr.getNextJarEntry();
			}
			jarStr.close();
			blobStr.close();
		}
		catch(Exception ex) {
			log.error("Error while checking for logic update", ex);
		}
	}
	
	/**
	 * If the class has not already been loaded, create it from the bytes we read from the blob.
	 */
	@Override
	public Class<?> loadClass(String clsName) throws ClassNotFoundException {
		if (classes.get(clsName) != null)
			return classes.get(clsName);
		String fileName = clsName.replaceAll("\\.", "/") + ".class";
		byte[] bytes = classBytes.get(fileName);
		if (bytes != null) {
			Class<?> cls = defineClass(clsName, bytes, 0, bytes.length);
			classes.put(clsName, cls);
			return cls;
		}
		if (parentClassLoader != null)
			return parentClassLoader.loadClass(clsName);
		return null;
	}

	/**
	 * Get the bytes for the given class.
	 */
	public byte[] getClassBytes(String clsName) {
		String fileName = clsName.replaceAll("\\.", "/") + ".class";
		return classBytes.get(fileName);
	}
	
	protected void forgetAllClasses() {
		classBytes = new HashMap<String, byte[]>();
		classes = new HashMap<String, Class<?>>();
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
 