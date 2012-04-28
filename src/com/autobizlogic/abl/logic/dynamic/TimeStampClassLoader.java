package com.autobizlogic.abl.logic.dynamic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import com.autobizlogic.abl.config.LogicConfiguration;
import com.autobizlogic.abl.config.LogicConfiguration.PropertyName;

/**
 * Class loader for individual class files in a directory structure. This simply finds the class
 * file based on the class name, and loads the file straight up.
 */
public class TimeStampClassLoader extends ClassLoader {

	private String basePath;
	private ClassLoader parentClassLoader;

	// For this next one, we used to use System.getProperty("file.separator");
	// but that evaluates to a backslash on Windows, which confuses String.replaceAll.
	private static final String slash = "/";

	/**
	 * basePath is the base directory where the class files can be found.
	 */
	public TimeStampClassLoader(String basePath, ClassLoader parentClassLoader) {
		this.basePath = basePath;
		if ( ! basePath.endsWith(slash))
			this.basePath += slash;

		this.parentClassLoader = parentClassLoader;
	}

	/**
	 * Load a class
	 */
	@Override
	public Class<?> loadClass(String clsName) throws ClassNotFoundException {
		
		String logicSuffix = LogicConfiguration.getInstance().getProperty(PropertyName.LOGIC_CLASS_SUFFIX);
		if ( ! clsName.endsWith(logicSuffix))
			return parentClassLoader.loadClass(clsName);

		String rawName = clsName.replaceAll("\\.", slash);
		String fullName = basePath + rawName + ".class";
		File classFile = new File(fullName);
		if ( ! classFile.exists()) {
			if (parentClassLoader != null)
				return parentClassLoader.loadClass(clsName);
			return null;
		}

        try {
    		@SuppressWarnings("deprecation")
			URL url = classFile.toURL();
            URLConnection connection = url.openConnection();
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int data = input.read();

            while(data != -1){
                buffer.write(data);
                data = input.read();
            }

            input.close();

            byte[] classData = buffer.toByteArray();

            return defineClass(clsName,
                    classData, 0, classData.length);

        } catch (Exception ex) {
        	throw new ClassNotFoundException("Unable to load class " + clsName, ex);
        }
    }
	
	@SuppressWarnings("deprecation")
	@Override
	public URL getResource(String name) {
		String fullName = basePath + name;
		File classFile = new File(fullName);
		if ( ! classFile.exists()) {
			if (parentClassLoader != null)
				return parentClassLoader.getResource(name);
			return null;
		}
		try {
			return classFile.toURL();
		} catch (MalformedURLException ex) {
			throw new RuntimeException("Error loading resource: " + name + 
					" from TimeStampClassLoader " + toString(), ex);
		}
	}
	
	@Override
	public String toString() {
		return "ABL logic class loader (" + this.hashCode() + ") for class directory: " + basePath;
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
 