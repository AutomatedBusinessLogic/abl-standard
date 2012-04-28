package com.autobizlogic.abl.data;

import java.lang.reflect.Method;

import com.autobizlogic.abl.metadata.MetaEntity;
import com.autobizlogic.abl.util.BeanNameUtil;

import javassist.util.proxy.MethodHandler;

/**
 * Handler for proxy objects backed by a PersistentBean.
 */
public class ProxyHandler implements MethodHandler {
	
	private final PersistentBean persBean;
	
	protected ProxyHandler(PersistentBean persBean) {
		this.persBean = persBean;
	}

	@Override
	public Object invoke(Object self, Method thisMethod, Method proceed,
			Object[] args) throws Throwable {
		
		MetaEntity metaEntity = persBean.getMetaEntity();
		if ("toString".equals(thisMethod.getName())) {
			return "Proxy for " + metaEntity.getEntityName() + 
						" : " + persBean.toString();
		}
		
		String propertyName = BeanNameUtil.getPropNameFromGetMethodName(thisMethod.getName());
		if (propertyName == null)
			return proceed.invoke(self, args);

		return persBean.get(propertyName);
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  ProxyHandler.java 83 2011-12-12 19:58:05Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 