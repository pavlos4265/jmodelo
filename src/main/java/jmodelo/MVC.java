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
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.zaxxer.hikari.HikariConfig;

public class MVC {
	public static boolean DEBUG;
	private List<String> areas;
	private String controllersPackage;
	private HikariConfig dbConfig;

	public MVC(String controllersPackage) {
		this(controllersPackage, false);
	}

	public MVC(String controllersPackage, boolean debug) {
		this(controllersPackage, debug, null, null);
	}

	public MVC(String controllersPackage, boolean debug, List<String> areas, HikariConfig dbConfig) {
		this.controllersPackage = controllersPackage;
		this.areas = areas;
		this.dbConfig = dbConfig;

		MVC.DEBUG = debug;
	}

	public void startHttp(int port) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0, "/", new MyHttpHandler(areas, controllersPackage, dbConfig));

		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();

		System.out.println("Server (http) started %d".formatted(port));
	}

	public void startHttps(int port, String keyStoreFile, String password) throws IOException, NoSuchAlgorithmException, UnrecoverableKeyException, 
	KeyManagementException, KeyStoreException, CertificateException {
		HttpsServer server = HttpsServer.create(new InetSocketAddress(port), 0, "/", new MyHttpHandler(areas, controllersPackage, dbConfig));

		server.setHttpsConfigurator(getHttpsConfigurator(createSSLContext(keyStoreFile, password)));

		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();

		System.out.println("Server (https) started %d".formatted(port));
	}

	private HttpsConfigurator getHttpsConfigurator(SSLContext context) throws NoSuchAlgorithmException {
		return new HttpsConfigurator(context) {
			@Override
			public void configure (HttpsParameters params) {
				SSLContext sslContext = getSSLContext();
				SSLParameters defaultSSLParameters = sslContext.getDefaultSSLParameters();
				params.setSSLParameters(defaultSSLParameters);
			}
		};
	}

	private SSLContext createSSLContext(String pkcs12File, String password) throws KeyStoreException, NoSuchAlgorithmException,
	CertificateException, IOException, UnrecoverableKeyException, KeyManagementException {
		KeyStore ks = KeyStore.getInstance ("PKCS12");
		ks.load ( new FileInputStream (pkcs12File), password.toCharArray() );

		KeyManagerFactory kmf = KeyManagerFactory.getInstance ( "SunX509" );
		kmf.init ( ks, password.toCharArray() );

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init ( kmf.getKeyManagers(), null, null );
		
		return sslContext;
	}

}
