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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

public class ViewInterpreter {
	private Pattern pattern;
	private ScriptEngine scriptEngine;
	private String area;
	private String controllerName;
	private Map<String, Object> viewData;
	private SimpleScriptContext context;

	private static Map<String, CompiledScript> scriptCache;

	public ViewInterpreter(ScriptEngine scriptEngine, String area, String controllerName, Map<String, Object> viewData) {
		this.scriptEngine = scriptEngine;
		this.area = area;
		this.controllerName = controllerName;	
		this.viewData = viewData;
		this.pattern = Pattern.compile("<%([\\s\\S]*?)%>");

		this.context = new SimpleScriptContext();

		scriptCache = scriptCache == null ?	Collections.synchronizedMap(new Cache<>(40)) : scriptCache;
	}

	public String parseView(String viewFile, String layoutFile, Object model) throws IOException, ScriptException {
		context.setAttribute("_partialView", viewFile, ScriptContext.ENGINE_SCOPE);
		
		return parsePartialView(layoutFile, model, true);
	}
	
	public String parsePartialView(String viewFile, Object model, boolean includeHelpers) throws IOException, ScriptException {
		String viewPath = Paths.get(viewFile).isAbsolute() ? viewFile : findViewPath(viewFile);

		CompiledScript compiledScript = scriptCache.get(viewPath);
		if (MVC.DEBUG || compiledScript == null) {
			File f = new File(viewPath);
			String contents = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			String finalScript =  "var _contents = \"\";\n" + generateViewScript(contents) + "\n_contents;";

			compiledScript = ((Compilable) scriptEngine).compile(finalScript);
			scriptCache.put(viewPath, compiledScript);
		}

		if (includeHelpers) scriptEngine.eval(getHelperFuncs(), this.context);
		
		context.setAttribute("_viewInterpreter", this, ScriptContext.ENGINE_SCOPE);
		context.setAttribute("_model", model, ScriptContext.ENGINE_SCOPE);
		context.setAttribute("_viewData", viewData, ScriptContext.ENGINE_SCOPE);
		return (String)compiledScript.eval(this.context);
	}

	private String generateViewScript(String contents) {
		Matcher matcher = pattern.matcher(contents);

		List<String> scriptParts = new ArrayList<>();
		while (matcher.find()) {
			String code = matcher.group(1);
			contents = contents.replaceFirst(Pattern.quote("<%" + code + "%>"), "<%%>");
			scriptParts.add(code);
		}

		String[] htmlParts = scriptParts.size() > 0 ? contents.split("<%%>") : new String[] {contents};

		for (String htmlPart: htmlParts) {
			contents = contents.replaceFirst(Pattern.quote(htmlPart), "_contents += '%s';"
					.formatted(htmlPart.replace("\r\n", "\n").replace("\n", "\\\\n")));
		}

		for (String scriptPart: scriptParts) {
			contents = contents.replaceFirst(Pattern.quote("<%%>"), scriptPart);
		}

		return contents;
	}

	private String getHelperFuncs() {
		return """
				function _partial(view, model) {_contents += _viewInterpreter.parsePartialView(view, model, false);}
				function _s(i) {_contents += i;}
				""";
	}

	private String findViewPath(String viewFile) throws FileNotFoundException {
		String areaFolder = area != null && !area.isBlank() ? area + "/" : "";
		String[] potentialPaths = new String[] { 
				"views/%s%s/%s".formatted(areaFolder, controllerName, viewFile),
				"views/%sshared/%s".formatted(areaFolder, viewFile),
		};

		for(String viewFilePath: potentialPaths) {
			File f = new File(viewFilePath);
			if (f.exists())
				return viewFilePath;
		}

		throw new FileNotFoundException(String.join(",", potentialPaths));
	}
}
