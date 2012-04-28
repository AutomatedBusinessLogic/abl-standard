package com.autobizlogic.abl.event.impl;

import org.hibernate.Transaction;

import com.autobizlogic.abl.logic.LogicContext;
import com.autobizlogic.abl.event.LogicEvent;
import com.autobizlogic.abl.event.LogicListener;

/**
 * A simple event listener that just prints out the events on stdout
 * 
 * Configure by: ABL Props, or API
 */
public class ConsoleLogListener implements LogicListener {

	@Override
	public void onLogicEvent(LogicEvent event) {
		Transaction tx = event.getContext().getSession().getTransaction();
		System.out.print("Tx " + tx.hashCode() + " ");
		LogicContext logicContext = event.getLogicContext();
		int depth = logicContext.getLogicNestLevel();
		for (int i = 0; i < depth; i++) {
			System.out.print("--->");
		}
		System.out.println(event);
		
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
 