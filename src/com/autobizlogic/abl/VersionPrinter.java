package com.autobizlogic.abl;

/**
 * This class contains a main method which is the default for the jar.
 * This allows people to execute the jar and get all relevant version information.
 * 
 * There are also methods to retrieve the same information programmatically.
 */
public class VersionPrinter {

	/**
	 * Prints the version of this library to System.out and returns.
	 * @param args Arguments are ignored.
	 */
	public static void main(String[] args) {
		
		System.out.println("Automated Business Logic engine version 2.1.5, build 0602, date 2012-04-28-14-13");
	}
	
	/**
	 * Get the version number for this library, typically in the format "2.0.6".
	 */
	public static String getVersion() {
		return "2.1.5";
	}
	
	/**
	 * Get the build number for this library, as a string.
	 */
	public static String getBuildNumber() {
		return "0602";
	}
	
	/**
	 * Get the build number for this library, as a number.
	 */
	public static int getBuildInteger() {
		return Integer.valueOf("0602");
	}
	
	/**
	 * Get the build date and time for this library, in the format "yyyy-MM-dd-HH-mm".
	 */
	public static String getBuildDate() {
		return "2012-04-28-14-13";
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
 