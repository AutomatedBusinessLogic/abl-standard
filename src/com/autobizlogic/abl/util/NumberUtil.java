package com.autobizlogic.abl.util;

import java.math.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class NumberUtil {

	/**
	 * Transform a number into the desired type
	 * @param number The number to transform
	 * @param toType The desired type
	 * @return The number in the new type
	 */
	public static Number convertNumberToType(Number number, Class<?> toType) {
		
		if (number == null)
			return null;
		
		if (number.getClass().equals(toType))
			return number;
		
		// The types are in order of (perceived) likeliness for efficiency
		if (toType.equals(Integer.class) || toType.equals(int.class)) {
			return number.intValue();
		}
		if (toType.equals(Long.class) || toType.equals(long.class)) {
			return number.longValue();
		}
		if (toType.equals(BigDecimal.class)) {
			return new BigDecimal(number.toString());
		}
		if (toType.equals(BigInteger.class)) {
			return new BigInteger(number.toString());
		}
		if (toType.equals(Byte.class) || toType.equals(byte.class)) {
			return number.byteValue();
		}
		if (toType.equals(Double.class) || toType.equals(double.class)) {
			return number.doubleValue();
		}
		if (toType.equals(Float.class) || toType.equals(float.class)) {
			return number.floatValue();
		}
		if (toType.equals(Short.class) || toType.equals(short.class)) {
			return number.shortValue();
		}
		if (toType.equals(AtomicInteger.class)) {
			return new AtomicInteger(number.intValue());
		}
		if (toType.equals(AtomicLong.class)) {
			return new AtomicLong(number.longValue());
		}
		
		throw new RuntimeException("Number is of unknown class: " + number.getClass());
	}
	
	/**
	 * Return the difference between two numbers of any type, i.e. n1 - n2
	 */
	public static BigDecimal difference(Number n1, Number n2) {
		BigDecimal bd1 = (BigDecimal)convertNumberToType(n1, BigDecimal.class);
		BigDecimal bd2 = (BigDecimal)convertNumberToType(n2, BigDecimal.class);
		return bd1.subtract(bd2);
	}
	
	/**
	 * Determine whether the two given numbers are equal, regardless of their type.
	 * @return False is either number is null, or if their values are not equal.
	 */
	public static boolean numbersAreEqual(Number n1, Number n2) {
		if (n1 == null || n2 == null)
			return false;
		BigDecimal bd1 = (BigDecimal)convertNumberToType(n1, BigDecimal.class);
		BigDecimal bd2 = (BigDecimal)convertNumberToType(n2, BigDecimal.class);
		return bd1.compareTo(bd2) == 0;
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
 