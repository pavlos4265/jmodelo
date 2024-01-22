/*   
 * Copyright 2023 pavlos4265
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jmodelo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

public abstract class Controller {
	private ScriptEngine scriptEngine;
	private String area;
	private HttpExchange exchange;
	private Cookies cookies;
	private Session session;
	private Connection databaseConnection;
	private Map<String, Object> viewData;

	public ActionResult html(String content) {
		return new ActionResult(content.getBytes(), "text/html;charset=utf-8", 200);
	}

	public ActionResult empty() {
		return new ActionResult("".getBytes(), "text/plain;charset=utf-8", 200);
	}

	public ActionResult file(File f, String mimeType) throws IOException {
		return new ActionResult(Files.readAllBytes(f.toPath()), mimeType, 200);
	}

	public ActionResult partialView(String viewFile) throws IOException, ScriptException {
		return partialView(viewFile, null);
	}

	public ActionResult partialView(String viewFile, Object model) throws IOException, ScriptException {
		ViewInterpreter viewInterpreter = new ViewInterpreter(scriptEngine, area, 
				this.getClass().getSimpleName().replace("Controller", ""), viewData, cookies, session);

		return new ActionResult(viewInterpreter.parsePartialView(viewFile, model, true).getBytes(), "text/html;charset=utf-8", 200);
	}

	public ActionResult view(String viewFile) throws IOException, ScriptException {
		return view(viewFile, null);
	}

	public ActionResult view(String viewFile, Object model) throws IOException, ScriptException {
		return view(viewFile, "layout.html", model);
	}

	public ActionResult view(String viewFile, String layoutFile, Object model) throws IOException, ScriptException {
		ViewInterpreter viewInterpreter = new ViewInterpreter(scriptEngine, area, 
				this.getClass().getSimpleName().replace("Controller", ""), viewData, cookies, session);

		return new ActionResult(viewInterpreter.parseView(viewFile, layoutFile, model).getBytes(), "text/html;charset=utf-8", 200);
	}

	public ActionResult json(Object o) {
		return new ActionResult( new Gson().toJson(o).getBytes(), "application/json", 200);
	}

	public ActionResult redirect(String url) {
		exchange.getResponseHeaders().add("Location", url);

		return new ActionResult("".getBytes(), "text/html", 303);
	}

	public void init(ScriptEngine scriptEngine, HttpExchange exchange, Cookies cookies, Session session, String area,
			Connection databaseConnection) {
		this.scriptEngine = scriptEngine;
		this.exchange = exchange;
		this.cookies = cookies;
		this.session = session;
		this.area = area;
		this.databaseConnection = databaseConnection;

		this.viewData = new HashMap<>();
	}

	public HttpExchange getExchange() {
		return exchange;
	}

	public Cookies getCookies() {
		return cookies;
	}

	public Session getSession() {
		return session;
	}

	public Connection getDatabaseConnection() {
		return databaseConnection;
	}

	public Map<String, Object> getViewData() {
		return viewData;
	}
}
