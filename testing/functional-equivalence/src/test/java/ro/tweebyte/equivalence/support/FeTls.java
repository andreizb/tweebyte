/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.support;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Installs a JVM-default TLS context so the FE harness's plain
 * {@link java.net.HttpURLConnection} calls speak TLS to the prod / functional-
 * equivalence stack. That stack runs with edge TLS + east-west mTLS on: the
 * functional-equivalence profile only layers a JaCoCo agent, it does NOT flip
 * SPRING_PROFILES_ACTIVE, so the base application.properties (server.ssl.enabled=true,
 * app.mtls.enabled=true) is in force.
 *
 * The installed context: - trusts the dev CA (truststore.p12) so the gateway + backend
 * server certs validate; - presents a client identity (gateway-service.p12) so the
 * backends' server.ssl.client-auth=need is satisfied on the direct /actuator/health
 * readiness probes. The gateway edge is client-auth=none, so scenario traffic needs only
 * the trust side — presenting a client cert there is simply ignored.
 *
 * Cert material lives under deployment/docker-compose/tls/ (gitignored, minted by
 * gen-certs.sh); passwords default to gen-certs.sh's `changeit` and are overridable via
 * the fe.tls.keystore.password / fe.tls.truststore.password system properties.
 */
public final class FeTls {

	private static boolean installed;

	private FeTls() {
	}

	/**
	 * Idempotently install the dev-CA trust + client identity as the JVM HTTPS default.
	 */
	public static synchronized void install() {
		if (installed) {
			return;
		}
		Path tlsDir = Path.of(System.getProperty("fe.repo.root", "."))
			.resolve("deployment/docker-compose/tls")
			.toAbsolutePath()
			.normalize();
		Path keystore = tlsDir.resolve("gateway-service.p12");
		Path truststore = tlsDir.resolve("truststore.p12");
		if (!Files.exists(keystore) || !Files.exists(truststore)) {
			throw new IllegalStateException("FE TLS material missing under " + tlsDir
					+ " — run deployment/docker-compose/tls/gen-certs.sh before the TLS-enabled FE suite.");
		}
		char[] ksPass = System.getProperty("fe.tls.keystore.password", "changeit").toCharArray();
		char[] tsPass = System.getProperty("fe.tls.truststore.password", "changeit").toCharArray();
		try {
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(load(keystore, ksPass), ksPass);
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(load(truststore, tsPass));
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
			installed = true;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to install FE TLS context from " + tlsDir, ex);
		}
	}

	private static KeyStore load(Path p12, char[] password) throws Exception {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		try (InputStream in = Files.newInputStream(p12)) {
			ks.load(in, password);
		}
		return ks;
	}

}
