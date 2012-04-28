package com.autobizlogic.abl.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * General utility methods.
 */
public class ObjectUtil {

	/**
	 * Given any two objects (null or not), compare them and return true if they are
	 * equal.
	 * <p/>
	 * This is trickier than just calling equals because:
	 * <ol>
	 * <li>the objects can be null
	 * <li>the objects can be numeric, in which case the data types will be ignored as much as possible
	 * <li>number types (e.g. int, float, BigDecimal) will be ignored as much as possible
	 * <li>string and char can be equal if the string is just one character long
	 * </ol>
	 * @param o1 Any object, or null
	 * @param o2 Any object, or null
	 * @return True if the two objects are equal. If both are null, returns true.
	 */
	@SuppressWarnings({ "unchecked" })
	public static boolean objectsAreEqual(Object o1, Object o2) {
		
		if (o1 == null && o2 == null)
			return true;
		
		if (o1 == null && o2 != null)
			return false;
		if (o1 != null && o2 == null)
			return false;
		
		if ((o1 instanceof Number) && !(o2 instanceof Number))
			return false;
		if ( !(o1 instanceof Number) && (o2 instanceof Number))
			return false;
		if ((o1 instanceof Number) && (o2 instanceof Number))
			return compareNumbers((Number)o1, (Number)o2);

		if ((o1 instanceof Comparable) && !(o2 instanceof Comparable))
			return false;
		if ( !(o1 instanceof Comparable) && (o2 instanceof Comparable))
			return false;
		if ((o1 instanceof Comparable) && (o2 instanceof Comparable) && o1.getClass().equals(o2.getClass()))
			return ((Comparable<Object>)o1).compareTo(o2) == 0;
		
		if (o1 instanceof String && o2 instanceof Character) {
			String s = "" + o2;
			return ((String)o1).compareTo(s) == 0;
		}
		if (o1 instanceof Character && o2 instanceof String) {
			String s = "" + o1;
			return ((String)o2).compareTo(s) == 0;
		}
		
		return o1.equals(o2);
	}
	
	/**
	 * Given two objects and the name of a property, determine whether that property has the same
	 * value in the two objects.
	 * @param propName The name of the property
	 * @param o1 The first object
	 * @param o2 The second object
	 * @return False if the value of the property is the same in both objects, or if both objects are null.
	 */
	public static boolean propertyHasChanged(String propName, Object o1, Object o2) {
		if (o1 == null && o2 == null)
			return false;
		
		if (o1 == null && o2 != null)
			return true;
		
		if (o2 == null && o1 != null)
			return true;
		
		if (o1 instanceof Map || o2 instanceof Map) {
			if ( ! (o1 instanceof Map) || ! (o2 instanceof Map))
				throw new RuntimeException("Cannot compare a map object and a non-map object");
			@SuppressWarnings("rawtypes")
			Map map1 = (Map)o1;
			@SuppressWarnings("rawtypes")
			Map map2 = (Map)o2;
			Object val1 = map1.get(propName);
			Object val2 = map2.get(propName);
			return !ObjectUtil.objectsAreEqual(val1, val2);
		}
		
		Object val1 = BeanUtil.getBeanProperty(o1, propName);
		Object val2 = BeanUtil.getBeanProperty(o2, propName);
		return !ObjectUtil.objectsAreEqual(val1, val2);
	}
	
	/**
	 * Get the value of the given property from an object, regardless of whether it is a map
	 * or a pojo.
	 */
	@SuppressWarnings("rawtypes")
	public static Object getProperty(Object obj, String propName) {
		if (obj instanceof Map)
			return ((Map)obj).get(propName);
		return BeanUtil.getBeanProperty(obj, propName);
	}
	
	/**
	 * Set the value of the given property in an object, regardless of whether it is a map
	 * or a pojo.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void setProperty(Object obj, String propName, Object value) {
		if (obj instanceof Map)
			((Map)obj).put(propName, value);
		else
			BeanUtil.setBeanProperty(obj, propName, value);
	}
	
	/**
	 * Compare two numbers of any kind, and determine if they are effectively equal.
	 * This works for nulls, BigDecimals, etc...
	 */
	public static boolean compareNumbers(Number n1, Number n2) {
		
		BigDecimal bd1 = null;
		if (n1 instanceof AtomicInteger)
			bd1 = new BigDecimal(((AtomicInteger)n1).intValue());
		else if (n1 instanceof AtomicLong)
			bd1 = new BigDecimal(((AtomicLong)n1).longValue());
		else if (n1 instanceof BigDecimal)
			bd1 = (BigDecimal)n1;
		else if (n1 instanceof BigInteger)
			bd1 = new BigDecimal((BigInteger)n1);
		else if (n1 instanceof Byte)
			bd1 = new BigDecimal((Byte)n1);
		else if (n1 instanceof Double)
			bd1 = new BigDecimal((Double)n1);
		else if (n1 instanceof Float)
			bd1 = new BigDecimal((Float)n1);
		else if (n1 instanceof Integer)
			bd1 = new BigDecimal((Integer)n1);
		else if (n1 instanceof Long)
			bd1 = new BigDecimal((Long)n1);
		else if (n1 instanceof Short)
			bd1 = new BigDecimal((Short)n1);
		else
			return n1.equals(n2);
		
		BigDecimal bd2 = null;
		if (n2 instanceof AtomicInteger)
			bd2 = new BigDecimal(((AtomicInteger)n2).intValue());
		else if (n2 instanceof AtomicLong)
			bd2 = new BigDecimal(((AtomicLong)n2).longValue());
		else if (n2 instanceof BigDecimal)
			bd2 = (BigDecimal)n2;
		else if (n2 instanceof BigInteger)
			bd2 = new BigDecimal((BigInteger)n2);
		else if (n2 instanceof Byte)
			bd2 = new BigDecimal((Byte)n2);
		else if (n2 instanceof Double)
			bd2 = new BigDecimal((Double)n2);
		else if (n2 instanceof Float)
			bd2 = new BigDecimal((Float)n2);
		else if (n2 instanceof Integer)
			bd2 = new BigDecimal((Integer)n2);
		else if (n2 instanceof Long)
			bd2 = new BigDecimal((Long)n2);
		else if (n2 instanceof Short)
			bd2 = new BigDecimal((Short)n2);
		else
			return n1.equals(n2);
		
		int maxScale = Math.max(bd1.scale(), bd2.scale());
		bd1 = bd1.setScale(maxScale);
		bd2 = bd2.setScale(maxScale);
		
		return bd1.compareTo(bd2) == 0;
	}
	
	/**
	 * Convert any type to any other type -- as much as we can.
	 * This will perform the following conversions:
	 * <ul>
	 * <li>if value is assignable to type, then value is simply returned
	 * <li>if value is a Number and type is a subclass of Number, value will be converted
	 * using NumberUtil.convertNumberToType()
	 * <li>if type is String, then value.toString() is returned
	 * <li>if type is Boolean, then:
	 * 		<ul>
	 * 		<li>if value is a boolean, it is returned
	 * 		<li>if value is a Number, then true is returned if it is non-zero
	 * 		<li>otherwise, value != null is returned
	 * 		</ul>
	 * <li>in all other cases, an exception is thrown (what else could you do?)
	 * </ul>
	 * @param value An object of any type (cannot be null)
	 * @param type
	 * @return
	 */
	public static Object convertToDataType(Object value, Class<?> type) {

		if (value == null)
			return null;
		
		if (type.isAssignableFrom(value.getClass()))
			return value;
		
		if (value instanceof Number && (Number.class.isAssignableFrom(type))) {
			return NumberUtil.convertNumberToType((Number)value, type);
		}
		
		if (type.isAssignableFrom(String.class))
			return value.toString();
		
		if (type.isAssignableFrom(Boolean.class)) {
			if (value instanceof Boolean)
				return value;
			if (value instanceof Number) {
				BigDecimal bdValue =  (BigDecimal)NumberUtil.convertNumberToType((Number)value, 
						BigDecimal.class);
				return bdValue.compareTo(BigDecimal.ZERO) != 0;
			}
			return true; // Evaluates to true if the object is not null
		}

		throw new RuntimeException("Unable to convert value " + value + " to type " + type);
	}
	
	/**
	 * Returns a string representation of the given map, even if the map's object graph contains
	 * itself. This is useful for Hibernate entities of mode MAP when the object may contain
	 * (usually indirect) references to itself, which causes toString to blow the stack because
	 * of infinite recursion.
	 * To visualize the problem, try the following bit of code:
	 * <blockquote>
	 * Map map = new HashMap();
	 * Map map2 = new HashMap();
	 * map.put("map2", map2);
	 * map2.put("map", map);
	 * System.out.println("Here is the map: " + map.toString()); // This blows up
	 * </blockquote>
	 * @param map A map
	 * @return A string representation of the map
	 */
	public static String mapToString(@SuppressWarnings("rawtypes") Map map) {
		if (map == null)
			return "null";
		int mapSize = 0;
		StringBuffer sb = new StringBuffer();
		
		@SuppressWarnings("unchecked")
		Set<Object> keys = map.keySet();
		for (Object key : keys) {
			mapSize++;
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(key + "=");
			Object value = map.get(key);
			try {
				if (value == null)
					sb.append("null");
				else if (value instanceof Collection)
					sb.append("[collection]");
				else if (value instanceof Map) {
					sb.append("<object>");
				}
				else
					sb.append(value.toString());
			}
			catch(Exception ex) {
				sb.append("[error showing value]");
			}
		}
		
		if (mapSize == 0)
			return "empty map";
		return sb.toString();
	}
	
	/**
	 * Return a string representation of the given object, even if it is null, or if it is a map
	 * that directly or indirectly contains itself.
	 * @param o
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static String safeToString(Object o) {
		if (o == null)
			return "null";
		
		if (o instanceof Map)
			return mapToString((Map)o);
		
		return o.toString();
	}
	
	/**
	 * Get the value of a field from an object, regardless of whether it is private or not.
	 * This is clearly undesirable 99.99% of the time, but sometimes you gotsa do what you gotsa do.
	 * @param object The object from which to retrieve the field value
	 * @param cls The class of the object. This is required in case object is a proxy.
	 * @param fieldName The name of the field
	 */
	public static Object getFieldValue(Object object, String fieldName) {
		Class<?> cls = object.getClass();
		Field field = getFieldFromClassWithInheritance(cls, fieldName);
		if (field == null) {
			String msg = "Unable to get property " + fieldName + 
					" of bean of type " + cls.getName() + 
					" because no accessible get/is method was found, and no accessible field was found either";
			throw new RuntimeException(msg);
		}
		return BeanUtil.getValueWithField(field, object);
	}
	
	/**
	 * Set the value of a field for an object using brute force.
	 */
	public static void setFieldValue(Object object, String fieldName, Object value) {
		Class<?> cls = object.getClass();
		Field field = getFieldFromClassWithInheritance(cls, fieldName);
		if (field == null) {
			String msg = "Unable to get property " + fieldName + 
					" of bean of type " + cls.getName() + 
					" because no accessible get/is method was found, and no accessible field was found either";
			throw new RuntimeException(msg);
		}
		BeanUtil.setValueWithField(field, object, value);
	}
	
	private static Field getFieldFromClass(Class<?> cls, String fieldName) {
		Field[] allFields = cls.getDeclaredFields();
		for (Field field : allFields) {
			if ( ! field.getName().equals(fieldName))
				continue;
			return field;
		}
		
		return null;
	}
	
	private static Field getFieldFromClassWithInheritance(Class<?> cls, String fieldName) {
		Field field = getFieldFromClass(cls, fieldName);
		while (field == null && ( ! cls.getName().equals("java.lang.Object"))) {
			cls = cls.getSuperclass();
			field = getFieldFromClass(cls, fieldName);
		}
		return field;
	}

	/**
	 * Using brute force, execute the specified method on the given object.
	 */
	public static void invokeMethodOnObject(Object object, String methodName, Object... args) {
		Class<?> cls = ProxyUtil.getNonProxyClass(object);
		Method method = getMethodFromClassWithInheritance(cls, methodName);
		Class<?>[] paramTypes = method.getParameterTypes();
		if ( ! (paramTypes.length == args.length))
			throw new RuntimeException("Unable to invoke method " + methodName + " on object " +
					object + " - invalid number of parameters specified, expected " + paramTypes.length +
					" but got " + args.length);
		try {
			method.invoke(object, args);
		}
			catch(Exception ex) {
				throw new RuntimeException("Exception while invoking method " + methodName +
						" on " + object, ex);
		}
	}
	
	private static Method getMethodFromClass(Class<?> cls, String methodName) {
		Method[] allMethods = cls.getDeclaredMethods();
		for (Method meth : allMethods) {
			if ( ! meth.getName().equals(methodName))
				continue;
			return meth;
		}
		return null;
	}
	
	private static Method getMethodFromClassWithInheritance(Class<?> cls, String methodName) {
		
		Method theMethod = getMethodFromClass(cls, methodName);
		while (theMethod == null && ( ! cls.getName().equals("java.lang.Object"))) {
			cls = cls.getSuperclass();
			theMethod = getMethodFromClass(cls, methodName);
		}
		return theMethod;
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
 