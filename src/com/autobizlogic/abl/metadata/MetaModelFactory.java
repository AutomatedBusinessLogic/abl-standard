package com.autobizlogic.abl.metadata;

import java.util.Map;
import java.util.WeakHashMap;

import org.hibernate.SessionFactory;

import com.autobizlogic.abl.metadata.hibernate.HibMetaModel;

public class MetaModelFactory {
	
	private static Map<SessionFactory, MetaModel> instances =
			new WeakHashMap<SessionFactory, MetaModel>();

	public static MetaModel getHibernateMetaModel(SessionFactory sessionFactory) {
		synchronized(instances) {
			MetaModel instance = instances.get(sessionFactory);
			if (instance == null) {
				if (instances.get(sessionFactory) == null) {
					instance = new HibMetaModel(sessionFactory);
					instances.put(sessionFactory, instance);
				}
			}
			return instance;
		}
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  MetaModelFactory.java 779 2012-02-21 08:43:35Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 