package com.autobizlogic.abl.mgmt;

import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import org.json.simple.JSONValue;

/**
 * The servlet used by the console to communicate with the business logic engine.
 * If you want to be able to view your business logic using the ABL Console,
 * you need to add the following to your web.xml:
 * <code>
 *  &lt;servlet>
 *	&lt;servlet-name>ABLConsoleServlet&lt;/servlet-name>
 *		&lt;servlet-class>com.autobizlogic.abl.mgmt.ConsoleServlet&lt;/servlet-class>
 *	&lt;/servlet>
 *	&lt;servlet-mapping>
 *		&lt;servlet-name>ABLConsoleServlet&lt;/servlet-name>
 *		&lt;url-pattern>/ABLConsoleServlet&lt;/url-pattern>
 *	&lt;/servlet-mapping>
 *	</code>
 * <p/>
 * This class is available only in the Professional edition of the ABL engine.
 */
public class ConsoleServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
			throws ServletException, java.io.IOException {
		
		Map<String, String> args = new HashMap<String, String>();
		Enumeration<String> paramNames = req.getParameterNames();
		while (paramNames.hasMoreElements()) {
			String paramName = paramNames.nextElement();
			String paramValue = req.getParameter(paramName);
			if (paramValue == null || paramValue.length() == 0)
				paramValue = null;
			args.put(paramName, paramValue);
		}
		

		Map<String, Object> result = null;
		String areaName = args.get("area");
		if (areaName.equals("SessionFactory"))
			result = SessionFactoryService.service(args);
		else if (areaName.equals("ClassMetadata"))
			result = ClassMetadataService.service(args);
		else if (areaName.equals("Transactions"))
			result = TransactionService.service(args);
		else if (areaName.equals("Performance"))
			result = PerformanceService.service(args);
		else if (areaName.equals("Dependency"))
			result = DependencyService.service(args);
		
		if (result == null)
			return;
		
		resp.setContentType("application/json");
		String resStr = JSONValue.toJSONString(result);
		PrintWriter out = resp.getWriter();
		out.print(resStr);
	}
	
	private static final long serialVersionUID = -3057097477062562429L;
}

/*
 * The contents of this file are subject to the Automated Business Logic Commercial License Version 1.0 (the "License").
 * You may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.automatedbusinesslogic.com/sales/license
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, 
 * either express or implied. See the License for the specific language governing rights and limitations under the License.
 */
 