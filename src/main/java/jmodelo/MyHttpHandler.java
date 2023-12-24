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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jmodelo.annotations.HttpPost;
import jmodelo.annotations.RawInput;
import jmodelo.annotations.UrlArg;

public class MyHttpHandler implements HttpHandler{

	private List<String> areas;
	private String controllersPackage;
	private ScriptEngine scriptEngine;
	private HikariDataSource dataSource;

	public MyHttpHandler(List<String> areas, String controllersPackage, HikariConfig dbConfig) {
		this.areas = areas;
		this.controllersPackage = controllersPackage;
		this.scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");

		this.dataSource = dbConfig != null ? new HikariDataSource(dbConfig) : null;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		String uri = exchange.getRequestURI().toString().contains("?") ? exchange.getRequestURI().toString().split("\\?")[0] :
			exchange.getRequestURI().toString();

		sendContent(exchange, uri.contains(".") ? readFile(exchange) : invokeAction(exchange));
	}

	private ActionResult readFile(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().toString();
		if (path.contains(".."))
			return notFound404();

		File f = new File("www%s".formatted(path));

		String[] fileNameParts = f.getName().split("\\.");
		String mimeType = getMimeType(fileNameParts[fileNameParts.length-1]);

		return f.exists() ? new ActionResult(Files.readAllBytes(f.toPath()), mimeType, 200) : notFound404();
	}

	private ActionResult invokeAction(HttpExchange exchange) 
			throws IOException {
		Cookies cookies = null;
		Session session = null;
		PostData postData = null;
		ActionResult result = null;

		try (Connection dbConnection = dataSource != null ? dataSource.getConnection() : null){
			PathInfo pathInfo = getPathInfo(exchange);

			Controller controller = (Controller)pathInfo.controllerClass().getConstructors()[0].newInstance();
			controller.init(scriptEngine, exchange, cookies = new Cookies(exchange), 
					session = new Session(cookies), pathInfo.areaName(), dbConnection);

			Map<String, String> params = exchange.getRequestURI().toString().contains("?") 
					? parseParameters(exchange.getRequestURI().toString().split("\\?")[1]) : new HashMap<>();

			postData = handlePostRequest(exchange);

			MethodWithParameters method = getMethodWithParameters(exchange.getRequestMethod(), pathInfo.controllerClass(), 
					pathInfo.actionName(), params, pathInfo.urlArgs(), postData);

			result = method.parameters() == null ? (ActionResult)method.method().invoke(controller) :
				(ActionResult)method.method().invoke(controller, method.parameters());
		}catch (InvocationTargetException e) {
			result = handleError(e.getCause());
		}catch (Exception e) {
			result = handleError(e);
		}

		if (session != null) session.storeSession();

		if (postData != null) {
			for (String key: postData.files().keySet()) {
				postData.files().get(key).tempFile().delete();
			}
		}

		return result;
	}

	private Optional<Class<?>> loadController(String areaName, String controllerName) {
		Class<?> controllerClass = null;
		try {
			controllerClass = Class.forName("%s%s.%sController"
					.formatted(controllersPackage, areaName != null ? ("." + areaName.toLowerCase()) : "", controllerName));
		} catch (ClassNotFoundException e) {}

		return Optional.ofNullable(controllerClass);
	}

	private MethodWithParameters getMethodWithParameters(String requestMethod, Class<?> controllerClass, String actionName,
			Map<String, String> params, List<String> urlArgs, PostData postData) throws NoSuchMethodException, SecurityException {
		List<Method> methods = Arrays.stream(controllerClass.getDeclaredMethods())
				.filter(x -> x.getName().equals(actionName)).collect(Collectors.toList());

		Method method = methods.stream()
				.filter(x -> (requestMethod.equals("POST") && x.getAnnotation(HttpPost.class) != null) 
						|| (requestMethod.equals("GET") && x.getAnnotation(HttpPost.class) == null))
				.findFirst().orElseThrow(() -> new NoSuchMethodException(actionName));

		if (method.getAnnotation(RawInput.class) == null) {
			params.keySet().forEach(x -> params.put(x, escapeInput(params.get(x))));
			urlArgs = urlArgs.stream().map(x -> escapeInput(x)).collect(Collectors.toList());
			
			if (postData != null)
				postData.fields().keySet()
				.forEach(x -> postData.fields().put(x, escapeInput(postData.fields().get(x))));		
		}

		List<Object> paramObjs = method.getParameterCount() > 0 ? getMethodParameters(method, params, urlArgs, postData) : null;

		return new MethodWithParameters(method, paramObjs != null ? paramObjs.toArray() : null);
	}

	private List<Object> getMethodParameters(Method method, Map<String, String> params, List<String> urlArgs, PostData postData) {
		List<Object> paramObjs = new ArrayList<>();
		for (Parameter param: method.getParameters()) {
			UrlArg urlArgAnnotation = null;
			String paramValue = (urlArgAnnotation = param.getAnnotation(UrlArg.class)) != null && urlArgAnnotation.value() < urlArgs.size()
					? urlArgs.get(urlArgAnnotation.value()) : params.get(param.getName());

			if (param.getType() == PostData.class) 
				paramObjs.add(postData);
			else if (param.getType() == GetParams.class)
				paramObjs.add(new GetParams(params));
			else if (paramValue == null) 
				paramObjs.add(null);
			else if (param.getType() == int.class)
				paramObjs.add(Integer.parseInt(paramValue));
			else if (param.getType() == double.class)
				paramObjs.add(Double.parseDouble(paramValue));
			else if (param.getType() == String.class)
				paramObjs.add(paramValue);
		}

		return paramObjs;
	}

	private PostData handlePostRequest(HttpExchange exchange) throws IOException {
		if (exchange.getRequestMethod().equals("POST")) {
			List<String> contentTypes = exchange.getRequestHeaders().get("Content-Type");
			String contentType = contentTypes != null && !contentTypes.isEmpty() ? contentTypes.getFirst() : null;

			if (contentType == null) return null; 

			if (contentType.startsWith("multipart/form-data"))
				return parseMultipart(exchange.getRequestBody(), contentType.split("boundary=")[1]);

			if (contentType.startsWith("application/x-www-form-urlencoded"))
				return new PostData(parseParameters(readLine(exchange.getRequestBody())), new HashMap<>());

			if (contentType.startsWith("application/json"))
				return parseJson(exchange.getRequestBody());

			PostFile postFile = new PostFile(null, contentType, readBytesAsFile(exchange.getRequestBody()));
			return new PostData(new HashMap<>(), Map.of("file", postFile));
		}

		return null;
	}

	private PostData parseMultipart(InputStream input, String boundary) throws IOException {
		Map<String, String> fields = new HashMap<>();
		Map<String, PostFile> files = new HashMap<>();

		String line;
		while(!(line = readLine(input)).equals("--")) {
			if (line.isBlank()) continue;

			Map<String, String> headerMap = parsePartHeader(input);
			Map<String, String> contentDisposition = parseSemicolonSeparated(headerMap.get("Content-Disposition"));

			String name = contentDisposition.get("name");
			String fileName = contentDisposition.get("filename");
			if (name == null) continue;
			if (fileName != null)
				files.put(name, new PostFile(fileName, headerMap.get("Content-Type"), readBytesUntilBoundaryAsFile(input, boundary)));
			else
				fields.put(name, readLine(input));
		}

		return new PostData(fields, files);
	}

	private Map<String, String> parsePartHeader(InputStream input) throws IOException {
		Map<String, String> values = new HashMap<>();

		String line;
		while ( !(line = readLine(input)).isBlank() ) {
			String[] lineParts = line.split(":");
			values.put(lineParts[0], lineParts[1]);
		}

		return values;
	}

	private File readBytesUntilBoundaryAsFile(InputStream input, String boundary) throws IOException {
		boundary = "\r\n--" + boundary;
		File f = new File("temp/%s.temp".formatted(UUID.randomUUID().toString()));
		try (BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(f, false))) {
			int bIndex = 0;
			byte[] temp = new byte[boundary.length()];
			int b;
			while ( (b = input.read()) != -1) {
				if (b == boundary.getBytes()[bIndex]) {
					temp[bIndex] = (byte)b;
					bIndex++;

					if (boundary.length() == bIndex) {
						fout.flush();
						return f;
					}
				}else if (bIndex > 0){
					fout.write(temp, 0, bIndex);
					bIndex = 0;
					fout.write(b);
				}else
					fout.write(b);
			}
		}

		return null;
	}

	private String readLine(InputStream input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		int b;
		while ((b = input.read()) != '\n' && b != -1) {
			if (b == '\r') continue;
			baos.write(b);
		}

		return new String(baos.toByteArray(), StandardCharsets.UTF_8);
	}

	private Map<String, String> parseSemicolonSeparated(String line) {
		Map<String, String> values = new HashMap<>();

		String[] parts = line.contains(";") ? line.split(";") : new String[] {line};
		Arrays.stream(parts).map(x -> x.contains("=") ? x.split("=") : new String[] {"", x})
		.forEach(x -> values.put(x[0].trim(), x[1].startsWith("\"") && x[1].endsWith("\"") 
				? x[1].substring(1, x[1].length()-1) : x[1]));

		return values;
	}

	private Map<String, String> parseParameters(String query) {
		Map<String, String> parameters = new HashMap<>();
		query = URLDecoder.decode(query, StandardCharsets.UTF_8);

		String[] pairs = query.contains("&") ? query.split("&") : new String[] {query};
		Arrays.stream(pairs).map(x -> x.split("=")).forEach(x -> parameters.put(x[0], x.length > 1 ? x[1] : ""));

		return parameters;
	}

	private File readBytesAsFile(InputStream input) throws IOException {
		byte[] buf = readBytes(input);

		File f = new File("temp/%s.temp".formatted(UUID.randomUUID().toString()));
		FileOutputStream output = new FileOutputStream(f, false);

		output.write(buf);
		output.close();

		return f;
	}

	private byte[] readBytes(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		byte[] buf = new byte[4096];
		int b;
		while ( (b = input.read(buf)) != -1 ) {
			output.write(buf, 0, b);
		}

		return output.toByteArray();
	}

	private PostData parseJson(InputStream input) throws IOException {
		String json = new String(readBytes(input), StandardCharsets.UTF_8);
		Type mapType = new TypeToken<Map<String, String>>(){}.getType();
		Map<String, String> fields = new Gson().fromJson(json, mapType);

		return new PostData(fields, new HashMap<>());
	}

	private void sendContent(HttpExchange exchange, ActionResult result) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", result.mimeType());

		OutputStream output;
		List<String> acceptEncoding;
		if ((acceptEncoding = exchange.getRequestHeaders().get("Accept-Encoding")) != null &&
				acceptEncoding.stream().anyMatch(x -> x.contains("gzip"))) {
			exchange.getResponseHeaders().add("Content-Encoding", "gzip");
			exchange.sendResponseHeaders(result.responseCode(), 0);
			output = new GZIPOutputStream (exchange.getResponseBody());
		}else {
			exchange.sendResponseHeaders(result.responseCode(), result.contentBuffer().length);
			output = exchange.getResponseBody();
		}
			
		output.write(result.contentBuffer());
		output.flush();
		output.close();
	}

	private ActionResult notFound404() throws IOException {
		String content = "<h1>404 - Not Found</h1>";

		return new ActionResult(content.getBytes(), "text/html", 404);
	}

	private ActionResult handleError(Throwable e) throws IOException {
		if (MVC.DEBUG) {
			StringBuilder sb = new StringBuilder("%s<br>".formatted(e.toString()));
			for(var st: e.getStackTrace())
				sb.append("%s<br>".formatted(st.toString()));

			String errorContent = ("<html><head></head><body bgcolor=\"gray\">"
					+ "<h3>%s</h3>"
					+ "</body></html>").formatted(sb.toString());
			return new ActionResult(errorContent.getBytes(), "text/html", 200);
		}

		return notFound404();
	}

	private PathInfo getPathInfo(HttpExchange exchange) throws ClassNotFoundException {
		String[] requestParts = exchange.getRequestURI().toString().contains("?") 
				? exchange.getRequestURI().toString().split("\\?")[0].split("/") : exchange.getRequestURI().toString().split("/");

		int idx = 1;
		String areaName = requestParts.length > idx && areas != null && areas.contains(requestParts[idx]) ?
				requestParts[idx++] : null;

		Class<?> controllerClass = requestParts.length > idx ? loadController(areaName, requestParts[idx]).orElse(null) : null;
		if (controllerClass == null)
			controllerClass = loadController(areaName, "Default").orElseThrow(
					() -> new ClassNotFoundException("%s/DefaultController".formatted(areaName)));
		else
			idx++;

		String actionName = requestParts.length > idx ? requestParts[idx++] : "index";

		List<String> urlArgs = new ArrayList<>();
		while (requestParts.length > idx) {
			urlArgs.add(requestParts[idx++]);
		}

		return new PathInfo(areaName, actionName, urlArgs, controllerClass);
	}
	
	private String escapeInput(String input) {
		input = input.replace("<", "&#x3C;")
				.replace(">", "&#x3E;")
				.replace("'", "&#x27;");

		return input;
	}

	private String getMimeType(String extension) {
		return switch (extension) {
		case "html", "htm" -> "text/html";
		case "txt" -> "text/plain";
		case "js" -> "text/javascript";
		case "css" -> "text/css";

		case "exe" -> "application/octet-stream";
		case "doc" -> "application/msword";
		case "pdf" -> "application/pdf";

		case "mp4" -> "video/mp4";

		case "jpeg", "jpg" -> "image/jpeg";
		case "gif" -> "image/gif";
		case "ico" -> "image/vnd.microsoft.icon";
		case "png" -> "image/png";
		case "svg" -> "image/svg+xml";
		case "webp" -> "image/webp";

		default -> "text/plain";
		};
	}
}
