package com.autobizlogic.abl.event;

/**
 * This interface is implemented by objects who want to be notified of all modified objects
 * when a transaction is committed.
 */
public interface TransactionSummaryListener {
	
	/**
	 * This method gets called once a transaction has been successfully committed.
	 */
	public void transactionCommitted(TransactionSummary summary);
}

/*
 * The contents of this file are subject to the Automated Business Logic Public License Version 1.0 (the "License"),
 * which is derived from the Mozilla Public License version 1.1. You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/license/public-license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 