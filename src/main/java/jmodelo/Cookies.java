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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

public class Cookies {
	private HttpExchange exchange;
	private Map<String, String> cookies;

	public Cookies(HttpExchange exchange) {
		this.exchange = exchange;

		parseCookies();
	}

	public String getCookie(String name) {
		return cookies.get(name);
	}

	public void addCookie(String name, String value, int seconds) {
		String headerValue = "%s=%s".formatted(name, value) + (seconds > 0 ? "; Max-Age=%d".formatted(seconds) : "");

		exchange.getResponseHeaders().add("Set-Cookie", headerValue);	

		cookies.put(name, value);
	}

	public void removeCookie(String name) {
		exchange.getResponseHeaders().add("Set-Cookie", "%s=%s; Max-Age=%d".formatted(name, "", 0));

		cookies.remove(name);
	}

	private void parseCookies() {
		cookies = new HashMap<String, String>();

		List<String> cookieValues = exchange.getRequestHeaders().get("Cookie");
		if (cookieValues != null) {
			for (String cookieStr: cookieValues) {
				String[] pairs = cookieStr.contains(";") ? cookieStr.split(";") : new String[] {cookieStr};

				for (String pair: pairs) {
					String[] parts = pair.split("=");
					cookies.put(parts[0], parts[1]);
				}
			}
		}
	}
}
