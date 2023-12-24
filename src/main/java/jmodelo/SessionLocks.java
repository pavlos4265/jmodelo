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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SessionLocks {

	private Map<String, Lock> locks;
	private static SessionLocks instance;
	
	public SessionLocks() {
		this.locks = new ConcurrentHashMap<>();
	}
	
	public Lock getLock(String uuid) {
		this.locks.putIfAbsent(uuid, new ReentrantLock());
		return locks.get(uuid);
	}
	
	public void removeLock(String uuid) {
		locks.remove(uuid);
	}
	
	public static SessionLocks getInstance() {
		return instance != null ? instance : (instance = new SessionLocks());
	}
	
}
