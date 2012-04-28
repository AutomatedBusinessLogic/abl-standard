package com.autobizlogic.abl.engine;

import javax.transaction.Synchronization;

/**
 * When a constraint fails, we need to make sure that the transaction will not be committed.
 * One obvious way to do this is to roll it back, but the problem with that approach is that
 * some frameworks (such as Grails) do not respond well to the transaction being thus aborted.
 * <p/>
 * Another obvious way would be to just throw an exception and let Hibernate deal with it. The
 * problem with that approach is that there could easily be some user code that would catch the
 * exception and bury it, which would cause the transaction to get committed.
 * <p/>
 * This class is used to guarantee that the transaction will be rolled back,
 * but without rolling it back right away. An instance of this class gets registered with the
 * transaction, and when it gets called, it throws an exception, thereby guaranteeing that
 * the transaction is not committed.
 */
public class TransactionFailureSynchronization implements Synchronization {
	
	private RuntimeException ex;
	
	public TransactionFailureSynchronization(RuntimeException ex) {
		this.ex = ex;
	}

	@Override
	public void afterCompletion(int status) {
		// Do nothing
	}

	@Override
	public void beforeCompletion() {
		throw ex;
	}

	@SuppressWarnings("unused")
	private final static String SVN_ID = "$Id: Version 2.1.5 Build 0602 Date 2012-04-28-14-13  TransactionFailureSynchronization.java 84 2011-12-12 19:59:30Z max@automatedbusinesslogic.com $";
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 