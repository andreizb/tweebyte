/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import javax.net.ssl.SSLException;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires east-west mTLS onto Spring Cloud Gateway's shared outbound Reactor Netty client.
 * The backend services require a client cert ({@code server.ssl.client-auth=need}), so the
 * gateway must present its own identity on every proxied downstream call. The {@code tweebyte}
 * SSL bundle supplies both the gateway's key material and the dev CA trust anchor; the
 * customizer secures the base client SCG has already configured with its pool settings, so the
 * declarative {@code spring.cloud.gateway.httpclient.pool.*} tuning is preserved.
 *
 * <p>Gated on {@code app.mtls.enabled}: unit tests do not define the bundle, so the bean is
 * absent there and the context loads against the framework-default client. The gateway sits
 * off the benchmark path, so this stays on in every runtime profile.
 *
 * @author Andrei Zbarcea
 */
@Configuration
@ConditionalOnProperty(name = "app.mtls.enabled", havingValue = "true")
public class HttpClientMtlsConfiguration {

	private static final String SSL_BUNDLE = "tweebyte";

	@Bean
	public HttpClientCustomizer mtlsHttpClientCustomizer(SslBundles sslBundles) {
		SslContext sslContext = clientSslContext(sslBundles);
		return httpClient -> httpClient.secure(spec -> spec.sslContext(sslContext));
	}

	private static SslContext clientSslContext(SslBundles sslBundles) {
		SslManagerBundle managers = sslBundles.getBundle(SSL_BUNDLE).getManagers();
		try {
			return SslContextBuilder.forClient()
				.keyManager(managers.getKeyManagerFactory())
				.trustManager(managers.getTrustManagerFactory())
				.build();
		}
		catch (SSLException ex) {
			throw new IllegalStateException("Failed to build the east-west mTLS client context", ex);
		}
	}

}
