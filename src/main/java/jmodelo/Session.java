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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Session {
	private Cookies cookies;
	private Map<String, String> values;
	private Lock lock;

	public Session(Cookies cookies) throws IOException {
		this.cookies = cookies;
		this.values = new HashMap<>();

		loadSession();
	}

	public void storeSession() throws IOException {
		String sessionId = cookies.getCookie("sessionid");

		if (sessionId != null) {
			File f = new File("sessions/%s.json".formatted(sessionId));

			String jsonData = new Gson().toJson(values);
			Files.writeString(f.toPath(), jsonData, StandardCharsets.UTF_8);

			if (this.lock != null) {
				SessionLocks.getInstance().removeLock(sessionId);
				this.lock.unlock();
			}
		}
	}

	public String getValue(String name) {
		return values.get(name);
	}

	public void addValue(String name, String value) {
		values.put(name, value);

		String sessionId = cookies.getCookie("sessionid");
		if (sessionId == null)
			cookies.addCookie("sessionid", UUID.randomUUID().toString(), 0);
	}

	public void removeValue(String name) {
		values.remove(name);
	}
	
	private void loadSession() throws IOException {
		String sessionId = cookies.getCookie("sessionid");

		if (sessionId != null) {
			this.lock = SessionLocks.getInstance().getLock(sessionId);
			this.lock.lock();
			File f = new File("sessions/%s.json".formatted(sessionId));

			if (f.exists()) {
				String jsonData = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

				Type mapType = new TypeToken<Map<String, String>>(){}.getType();
				values = new Gson().fromJson(jsonData, mapType);
			}
		}
	}
}
