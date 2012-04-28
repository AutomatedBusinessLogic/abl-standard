package com.autobizlogic.abl.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.beanutils.BeanMap;

import com.autobizlogic.abl.data.PersistentBean;
import com.autobizlogic.abl.metadata.MetaAttribute;
import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.metadata.MetaRole;

/**
 * Utility methods to manipulate Java beans.
 */
public class BeanUtil {
	
	/**
	 * Remember which get methods or fields have already been seen
	 */
	private static final Map<String, AccessibleObject> getAccessibles = 
			new ConcurrentHashMap<String, AccessibleObject>();
	
	/**
	 * Remember which set methods or fields have already been seen
	 */
	private static final Map<String, AccessibleObject> setAccessibles = 
			new ConcurrentHashMap<String, AccessibleObject>();
	
	/**
	 * Get a short string describing the given bean, in the form EntityName[primary-key]
	 */
	@SuppressWarnings("rawtypes")
	public static String getBeanDescription(MetaEntity metaEntity, Object bean) {
		
		Map map = null;
		if (bean instanceof Map)
			map = (Map)bean;
		else
			map = new BeanMap(bean);
		
		String pkPropName = metaEntity.getIdentifierName();
		if (pkPropName != null) {
			Object pk = map.get(pkPropName);
			return metaEntity.getEntityName() + "[" + pk.toString() + "]";
		}
		
		return metaEntity.getEntityName() + "[composite key]";
	}
	
	/**
	 * Get a string showing the given bean's full value (primary key and all attributes)
	 */
	@SuppressWarnings("rawtypes")
	public static String getBeanLongDescription(MetaEntity metaEntity, Object bean) {
		
		StringBuffer result = new StringBuffer();
		
		result.append(getBeanDescription(metaEntity, bean));
		
		Map map = null;
		if (bean instanceof Map)
			map = (Map)bean;
		else
			map = new BeanMap(bean);

		result.append(" = [");
		Set<MetaAttribute> metaAttributes = metaEntity.getMetaAttributes();
		for (MetaAttribute metaAttribute: metaAttributes) {
			Object value = map.get(metaAttribute.getName());
			result.append(metaAttribute.getName());
			result.append("=");
			result.append(value);
			result.append(", ");
		}
		result.append("]");
		
		return result.toString();
	}
	
	/**
	 * Get the value of the given property for the given bean.
	 * @param bean The bean in question
	 * @param propertyName The name of the property
	 * @return The value of the property. If not found, an exception is thrown.
	 */
	public static Object getBeanProperty(Object bean, String propertyName) {
		// First, try to find a getter method.
		Class<?> beanClass = ProxyUtil.getNonProxyClass(bean);

		String propertyKey = beanClass.getName() + "/" + propertyName;
		AccessibleObject getter = getAccessibles.get(propertyKey);
		if (getter != null) {
			if (getter instanceof Method) {
				return getValueWithMethod((Method)getter, bean);
			}
			
			return getValueWithField((Field)getter, bean);
		}
		
		String getterName = "get" + propertyName.substring(0, 1).toUpperCase();
		if (propertyName.length() > 1)
			getterName += propertyName.substring(1);
		String isName = "is" + propertyName.substring(0, 1).toUpperCase();
		if (propertyName.length() > 1)
			isName += propertyName.substring(1);

		Method getterMethod = getMethodFromClassWithInheritance(beanClass, getterName, null);
		if (getterMethod == null)
			getterMethod = getMethodFromClassWithInheritance(beanClass, isName, null);
		if (getterMethod != null) {
			Object value = getValueWithMethod(getterMethod, bean);
			getAccessibles.put(propertyKey, getterMethod);
			return value;
		}
		
		// No getter method found: go straight for the field
		Field field = getFieldFromClassWithInheritance(beanClass, propertyName, null);
		if (field == null) {
			String msg = "Unable to get property " + propertyName + 
					" of bean of type " + beanClass.getName() + 
					" because no accessible get/is method was found, and no accessible field was found either";
			if (beanClass.getSimpleName().contains("Hash"))
				msg += "\nfrom bean: " + ObjectUtil.safeToString(bean);
			throw new RuntimeException(msg);
		}
		Object value = getValueWithField(field, bean);
		getAccessibles.put(propertyKey, field);
		return value;
	}
	
	/**
	 * Set the given bean's given property to the value in the given PersistentBean. If unsuccessful, an exception
	 * is thrown.
	 */
	public static void setBeanPropertyToPersistentBean(Object bean, String propertyName, PersistentBean value) {
		if (value.getMetaEntity().isPojo())
			BeanUtil.setBeanProperty(bean, propertyName, value.getBean());
		else if (value.getMetaEntity().isMap())
			BeanUtil.setBeanProperty(bean, propertyName, value.getMap());
		else
			throw new RuntimeException("Internal error: object is neither pojo nor map");
	}

	/**
	 * Set the given bean's given property to the given value. If unsuccessful, an exception
	 * is thrown.
	 */
	public static void setBeanProperty(Object bean, String propertyName, Object value) {
		
		// First, try to find a setter method.
		Class<?> beanClass = ProxyUtil.getNonProxyClass(bean);

		String propertyKey = beanClass.getName() + "/" + propertyName;
		AccessibleObject setter = setAccessibles.get(propertyKey);
		if (setter != null) {
			if (setter instanceof Method) {
				setValueWithMethod((Method)setter, bean, value);
			}
			else {
				setValueWithField((Field)setter, bean, value);
			}
			return;
		}
		
		String setterName = "set" + propertyName.substring(0, 1).toUpperCase();
		if (propertyName.length() > 1)
			setterName += propertyName.substring(1);

		Class<?> valueClass = null;
		if (value != null)
			valueClass = ProxyUtil.getNonProxyClass(value);
		Method setterMethod = getMethodFromClassWithInheritance(beanClass, setterName, valueClass);
		if (setterMethod != null) {
			setValueWithMethod(setterMethod, bean, value);
			setAccessibles.put(propertyKey, setterMethod);
			return;
		}
		
		// No setter method found: go straight for the field
		Field field = getFieldFromClassWithInheritance(beanClass, propertyName, valueClass);
		if (field == null)
			throw new RuntimeException("Unable to set property " + propertyName + 
					" of bean of type " + beanClass.getName() + 
					" because no accessible set method was found, and no accessible field was found either");
		setValueWithField(field, bean, value);
		setAccessibles.put(propertyKey, field);
	}
	
	/**
	 * Determine whether the given bean has a property with the given name.
	 * This does not follow the Javabeans conventions: first we look for a getX or isX
	 * method, then if we don't find it, we look for a field.
	 */
	public static boolean beanHasProperty(Object bean, String propertyName) {
		Class<?> beanClass = ProxyUtil.getNonProxyClass(bean);

		// Is the accessor already cached?
		String propertyKey = beanClass.getName() + "/" + propertyName;
		AccessibleObject getter = getAccessibles.get(propertyKey);
		if (getter != null)
				return true;
		
		String getterName = "get" + propertyName.substring(0, 1).toUpperCase();
		if (propertyName.length() > 1)
			getterName += propertyName.substring(1);
		String isName = "is" + propertyName.substring(0, 1).toUpperCase();
		if (propertyName.length() > 1)
			isName += propertyName.substring(1);

		Method getterMethod = getMethodFromClassWithInheritance(beanClass, getterName, null);
		if (getterMethod == null)
			getterMethod = getMethodFromClassWithInheritance(beanClass, isName, null);
		if (getterMethod != null) {
			getAccessibles.put(propertyKey, getterMethod);
			return true;
		}
		
		// No get/is method found: go straight for the field
		Field field = getFieldFromClassWithInheritance(beanClass, propertyName, null);
		if (field == null) {
			return false;
		}
		getAccessibles.put(propertyKey, field);
		return true;
	}
	
	/**
	 * Given two beans of the same entity, determine whether they are equal. This is done by
	 * looking at the value of all the attributes, and the value of all the parent
	 * relationships. If there is any difference, false is returned.
	 */
	@SuppressWarnings("rawtypes")
	public static boolean beansAreEqual(MetaEntity metaEntity, Object bean1, Object bean2) {
		
		// First the easy cases
		if (bean1 == null && bean2 == null)
			return true;
		if (bean1 == null && bean2 != null)
			return false;
		if (bean1 != null && bean2 == null)
			return false;
		if (bean1 == bean2) // You never know...
			return true;
		
		Map beanMap1 = null;
		Map beanMap2 = null;
		if (metaEntity.isMap()) {
			beanMap1 = (Map)bean1;
			beanMap2 = (Map)bean2;
		}
		else {
			beanMap1 = new BeanMap(bean1);
			beanMap2 = new BeanMap(bean2);
		}
		
		// Compare the attributes - return at first difference
		Set<MetaAttribute> metaAttributes = metaEntity.getMetaAttributes();
		for (MetaAttribute metaAttribute : metaAttributes) {
			Object val1 = beanMap1.get(metaAttribute.getName());
			Object val2 = beanMap2.get(metaAttribute.getName());
			if (val1 == null && val2 == null)
				continue;
			if (val1 != null && val2 != null && val1 == val2)
				continue;
			if (val1 != null && val2 != null && val1.equals(val2))
				continue;
			
			return false;
		}
		
		Set<MetaRole> metaRoles = metaEntity.getRolesFromChildToParents();
		for (MetaRole metaRole : metaRoles) {
			Object val1 = beanMap1.get(metaRole.getRoleName());
			Object val2 = beanMap2.get(metaRole.getRoleName());
			if (val1 == null && val2 == null)
				continue;
			if (val1 != null && val2 != null && val1 == val2)
				continue;
			if (val1 != null && val2 != null && val1.equals(val2))
				continue;
			
			return false;
		}		

		return true;
	}
	
	/**
	 * Reset all caches. This is called internally when the logic classes are updated.
	 */
	public static void resetCaches() {
		getAccessibles.clear();
		setAccessibles.clear();
	}
	
	//////////////////////////////////////////////////////////////////////////////////////
	// Method methods
	
	private static Object getValueWithMethod(Method getterMethod, Object bean) {

		try {
			getterMethod.setAccessible(true);
			return getterMethod.invoke(bean);
		}
		catch(Exception ex) {
			throw new RuntimeException("Unable to use get method " + getterMethod.getName() + 
					" on instance of " + bean.getClass().getName(), ex);
		}
	}
	
	private static void setValueWithMethod(Method setterMethod, Object bean, Object value) {

		try {
			setterMethod.setAccessible(true);
			setterMethod.invoke(bean, value);
		}
		catch(Exception ex) {
			throw new RuntimeException("Unable to use set method " + setterMethod.getName() + 
					" on instance of " + bean.getClass().getName(), ex);
		}
	}
	
	/**
	 * Get the method with the given name from the given class, provided that it takes one argument
	 * of the provided type.
	 * @param cls The class who should have (or inherit) the method
	 * @param methodName The name of the method
	 * @param argClass If provided, the type of the sole argument to the method. If null, no argument is assumed.
	 * @param onlyProtectedAndHigher If true, we will ignore private methods in the superclasses.
	 * @return The method if found, otherwise null.
	 */
	private static Method getMethodFromClass(Class<?> cls, String methodName, Class<?> argClass, 
			boolean onlyProtectedAndHigher) {
		
		Method[] allMethods = cls.getDeclaredMethods();
		for (Method meth : allMethods) {
			if ( ! meth.getName().equals(methodName))
				continue;
			
			if (onlyProtectedAndHigher) {
				int modifiers = meth.getModifiers();
				if (Modifier.isPrivate(modifiers))
					continue;
			}
			
			if (argClass != null) {
				Class<?>[] paramTypes = meth.getParameterTypes();
				if (paramTypes.length != 1)
					continue;

				Class<?> genericType = getGenericType(paramTypes[0]);
				if ( ! genericType.isAssignableFrom(argClass))
					continue;
			}
			
			// Note that if we're trying to set a value to null, we obviously cannot check the
			// signature for overloading, and therefore we'll return the first method which takes
			// one parameter. I think that's not that unreasonable, but it could conceivably break
			// if someone does funny things with their bean.
			
			return meth;
		}
		
		return null;
	}
	
	private static Method getMethodFromClassWithInheritance(Class<?> cls, String methodName, 
			Class<?> argClass) {
		
		Method theMethod = getMethodFromClass(cls, methodName, argClass, false);
		while (theMethod == null && ( ! cls.getName().equals("java.lang.Object"))) {
			cls = cls.getSuperclass();
			theMethod = getMethodFromClass(cls, methodName, argClass, true);
		}
		return theMethod;
	}
	
	//////////////////////
	// Field methods
	
	protected static Object getValueWithField(Field field, Object bean) {

		try {
			field.setAccessible(true);
			return field.get(bean);
		}
		catch(Exception ex) {
			throw new RuntimeException("Unable to get property " + field.getName() + 
					" on instance of " + bean.getClass().getName() + 
					" using field " + field.getName(), ex);
		}
	}

	protected static void setValueWithField(Field field, Object bean, Object value) {

		try {
			field.setAccessible(true);
			field.set(bean, value);
		}
		catch(Exception ex) {
			String extraInfo = "";
			if (value != null) {
				// If the two classes have the same name, it's probably a classloader problem,
				// so we generate more informative output to help debug
				if (field.getType().getName().equals(value.getClass().getName())) {
					extraInfo = ". It looks like the two classes have the same name, so this is " +
							"probably a classloader issue. The bean field's class comes from " +
							field.getType().getClassLoader() + ", the bean itself comes from " + 
							bean.getClass().getClassLoader() +
							", the value class comes from " + value.getClass().getClassLoader();
				}
			}
			throw new RuntimeException("Unable to set property " + field.getName() + 
					" on instance of " + bean.getClass().getName() + 
					" using field " + field.getName() + extraInfo, ex);
		}
	}

	private static Field getFieldFromClass(Class<?> cls, String fieldName, Class<?> argClass,
			boolean onlyProtectedAndHigher) {
		Field[] allFields = cls.getDeclaredFields();
		for (Field field : allFields) {
			if ( ! field.getName().equals(fieldName))
				continue;
			int modifiers = field.getModifiers();
			if (onlyProtectedAndHigher && Modifier.isPrivate(modifiers))
				continue;
			if (argClass != null) {
				Class<?> genericType = getGenericType(field.getType());
				if ( ! genericType.isAssignableFrom(argClass)) {
					String extraInfo = "";
					// If the two classes have the same name, it's probably a classloader problem,
					// so we generate more informative output to help debug
					if (field.getType().getName().equals(argClass.getName())) {
						extraInfo = ". It looks like the two classes have the same name, so this is " +
								"probably a classloader issue. The bean field's class comes from " +
								field.getType().getClassLoader() + ", the other class comes from " +
								argClass.getClassLoader();
					}
					throw new RuntimeException("Bean field " + fieldName + " of class " + cls.getName() + 
							" is of the wrong type (" + field.getType().getName() + ") for the given argument, " +
							"which is of type " + argClass.getName() + extraInfo);
				}
			}
			
			return field;
		}
		
		return null;
	}
	
	private static Field getFieldFromClassWithInheritance(Class<?> cls, String fieldName, Class<?> argClass) {
		Field field = getFieldFromClass(cls, fieldName, argClass, false);
		while (field == null && ( ! cls.getName().equals("java.lang.Object"))) {
			cls = cls.getSuperclass();
			field = getFieldFromClass(cls, fieldName, argClass, true);
		}
		return field;
	}
	
	/**
	 * Unfortunately, int.class.isAssignableFrom(Integer.class) returns false, so we need
	 * to convert primitive types to their class equivalent.
	 * @param cls Any class
	 * @return If the class is a primitive class (e.g. int.class, boolean.class, etc...)
	 * return the class equivalent (e.g. Integer.class, Boolean.class, etc...), otherwise
	 * return the given class.
	 */
	private static Class<?> getGenericType(Class<?> cls) {
		if (cls.equals(byte.class))
			return Byte.class;
		if (cls.equals(short.class))
			return Short.class;
		if (cls.equals(int.class))
			return Integer.class;
		if (cls.equals(long.class))
			return Long.class;
		if (cls.equals(float.class))
			return Float.class;
		if (cls.equals(double.class))
			return Double.class;
		if (cls.equals(boolean.class))
			return Boolean.class;
		if (cls.equals(char.class))
			return Character.class;
		return cls;
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
 