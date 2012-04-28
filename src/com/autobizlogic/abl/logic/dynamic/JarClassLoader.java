package com.autobizlogic.abl.logic.dynamic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Class loader for a given jar file.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class JarClassLoader extends ClassLoader {

	private final JarFile jarFile;
	private ClassLoader parentClassLoader;

	/**
	 * Create a JarClassLoader for the given jar.
	 * @param jarPath The path of the jar file
	 * @param parentClassLoader The optional parent class loader which, if specified,
	 * will be used to load classes other than those in the jar (e.g. java.lang.Object).
	 */
	public JarClassLoader(String jarPath, ClassLoader parentClassLoader) {
		File file = new File(jarPath);
		if ( ! file.exists())
			throw new RuntimeException("Unable to find jar file: " + jarPath);
		if ( ! file.canRead())
			throw new RuntimeException("Cannot read jar file: " + jarPath);
		try {
			this.jarFile = new JarFile(file);
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while reading jar file: " + jarPath, ex);
		}
		
		this.parentClassLoader = parentClassLoader;
	}
	
	/**
	 * Note that clsName must be preceded by a # to load a logic class.
	 * This allows us to easily distinguish between a logic class and a non-logic class
	 * when e.g. java.lang.Object is requested.
	 */
	@Override
	public Class<?> loadClass(String clsName) throws ClassNotFoundException {
		
        byte[] classData = getClassBytes(clsName);
        if (classData == null) {
        	if (parentClassLoader != null)
        		return parentClassLoader.loadClass(clsName);
        	
        	throw new RuntimeException("Unable to find bytecode for class: " + clsName);
        }
        return defineClass(clsName, classData, 0, classData.length);
	}
	
	/**
	 * Get the bytes for the given class.
	 */
	public byte[] getClassBytes(String clsName) {
		String fileName = clsName.replaceAll("\\.", "/");			
		ZipEntry zipEntry = jarFile.getEntry(fileName + ".class");
		if (zipEntry == null) {
			return null;
		}
		
		ByteArrayOutputStream buffer = null;
		try {
	        InputStream input = jarFile.getInputStream(zipEntry);
	        buffer = new ByteArrayOutputStream();
	        int data = input.read();
	
	        while(data != -1){
	            buffer.write(data);
	            data = input.read();
	        }
	
	        input.close();
		}
		catch(Exception ex) {
			throw new RuntimeException("Error while loading class " + clsName + 
					" from jar file " + jarFile.getName(), ex);
		}

        byte[] classData = buffer.toByteArray();
        return classData;
	}
	
	@Override
	public URL getResource(String name) {
		if (jarFile.getEntry(name) == null) {
			if (parentClassLoader != null)
				return parentClassLoader.getResource(name);
			return null;
		}
		String fullName = jarFile.getName() + "!" + name;
		try {
			return new URL("jar", "", fullName);
		} catch (MalformedURLException ex) {
			throw new RuntimeException("Error retrieving resource " + name +
					" from JarClassLoader: " + toString(), ex);
		}
	}
	
	@Override
	public String toString() {
		return "ABL logic class loader (" + this.hashCode() + ") for jar file: " + jarFile.getName();
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
 