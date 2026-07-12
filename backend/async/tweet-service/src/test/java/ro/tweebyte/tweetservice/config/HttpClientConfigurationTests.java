/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.lang.reflect.Field;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestClientCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HttpClientConfiguration}.
 * Covers the mTLS-disabled path (benchmark default) and the customizer bean.
 * The mTLS-enabled path requires a real SSL bundle and is integration-only.
 */
@ExtendWith(MockitoExtension.class)
class HttpClientConfigurationTests {

	@Mock
	private SslBundles sslBundles;

	@Test
	void pooledHttpClient_mtlsDisabled_returnsNonNull() throws Exception {
		HttpClientConfiguration config = defaultConfig();
		try (CloseableHttpClient client = config.pooledHttpClient(false, this.sslBundles)) {
			assertThat(client).isNotNull();
		}
	}

	@Test
	void pooledHttpClient_mtlsDisabled_closeableWithoutError() throws Exception {
		HttpClientConfiguration config = defaultConfig();
		CloseableHttpClient client = config.pooledHttpClient(false, this.sslBundles);
		assertThat(client).isNotNull();
		client.close();
	}

	@Test
	void pooledRestClientCustomizer_returnsNonNull() throws Exception {
		HttpClientConfiguration config = defaultConfig();
		CloseableHttpClient client = config.pooledHttpClient(false, this.sslBundles);
		RestClientCustomizer customizer = config.pooledRestClientCustomizer(client);
		assertThat(customizer).isNotNull();
		client.close();
	}

	@Test
	void pooledRestClientCustomizer_acceptsBuilderWithoutThrowing() throws Exception {
		HttpClientConfiguration config = defaultConfig();
		CloseableHttpClient client = config.pooledHttpClient(false, this.sslBundles);
		RestClientCustomizer customizer = config.pooledRestClientCustomizer(client);
		// Calling customize with a real builder should not throw
		org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder();
		assertThat(customizer).isNotNull();
		customizer.customize(builder);
		client.close();
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	private static HttpClientConfiguration defaultConfig() throws Exception {
		HttpClientConfiguration config = new HttpClientConfiguration();
		setField(config, "maxConnectionsPerRoute", 10);
		setField(config, "maxConnectionsTotal", 20);
		setField(config, "acquireTimeoutSeconds", 5L);
		setField(config, "responseTimeoutSeconds", 5L);
		return config;
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

}
