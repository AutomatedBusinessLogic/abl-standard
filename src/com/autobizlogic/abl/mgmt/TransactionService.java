package com.autobizlogic.abl.mgmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.autobizlogic.abl.event.AsyncLogicEventListener;
import com.autobizlogic.abl.event.GlobalLogicEventHandler;
import com.autobizlogic.abl.event.LogicListener;
import com.autobizlogic.abl.event.LogicListenerHandler;

/**
 * The management service for transaction information.
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class TransactionService {

	public static Map<String, Object> service(Map<String, String> args) {
		
		String serviceName = args.get("service");
		if (serviceName.equals("controlService"))
			return controlService(args);
		if (serviceName.equals("getTransactionList"))
			return getTransactionList(args);
		if (serviceName.equals("getTransaction"))
			return getTransaction(args);

		return null;
	}

	public static Map<String, Object> controlService(Map<String, String> args) {
		HashMap<String, Object> result = new HashMap<String, Object>();
		LogicListenerHandler handler = GlobalLogicEventHandler.getGlobalLogicListenerHandler();
		
		String command = args.get("command");
		if ("start".equals(command) && ! handler.hasLogicListenerOfClass(AsyncLogicEventListener.class)) {
			synchronized(handler) {
				if (! handler.hasLogicListenerOfClass(AsyncLogicEventListener.class)) {
					handler.addLogicListener(new AsyncLogicEventListener());
				}
			}
		}
		if ("stop".equals(command)) {
			synchronized(handler) {
				handler.removeLogicListenersOfClass(AsyncLogicEventListener.class);
			}
		}
		if ("status".equals(command)) {
			boolean running = false;
			synchronized(handler) {
				List<LogicListener> listeners = handler.getLogicListeners();
				for (LogicListener l : listeners) {
					if (l instanceof AsyncLogicEventListener) {
						running = true;
						break;
					}
				}
			}
			result.put("data", running);
		}

		return result;
	}
	
	public static Map<String, Object> getTransactionList(Map<String, String> args) {

		HashMap<String, Object> result = new HashMap<String, Object>();

		AsyncLogicEventListener listener = getListener();
		if (listener == null)
			return result;
		
		List<Map<String, Object>> txs = listener.getAllTransactions();
		result.put("data", txs);
		
		return result;
	}
	
	public static Map<String, Object> getTransaction(Map<String, String> args) {
		HashMap<String, Object> result = new HashMap<String, Object>();

		AsyncLogicEventListener listener = getListener();
		if (listener == null)
			return result;
		
		String txId = args.get("txId");
		Map<String, Object> tx = listener.getTransaction(txId);
		result.put("data", tx);
		
		return result;
	}
	
	private static AsyncLogicEventListener getListener() {
		List<LogicListener> listeners = GlobalLogicEventHandler.getGlobalLogicListenerHandler().getLogicListeners();
		for (LogicListener l : listeners) {
			if (l instanceof AsyncLogicEventListener) {
				return (AsyncLogicEventListener)l;
			}
		}
		return null;
	}
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 