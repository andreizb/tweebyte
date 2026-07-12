/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Exercises HttpClientConfiguration — specifically the mtlsEnabled branches
 * in {@code pooledHttpClient}: the false-branch (plaintext) and the true-branch
 * that attempts to resolve the SSL bundle.
 */
class HttpClientConfigurationTests {

	private HttpClientConfiguration buildConfig(int maxPerRoute, int maxTotal, long acquireTimeout,
			long responseTimeout) {
		HttpClientConfiguration config = new HttpClientConfiguration();
		ReflectionTestUtils.setField(config, "maxConnectionsPerRoute", maxPerRoute);
		ReflectionTestUtils.setField(config, "maxConnectionsTotal", maxTotal);
		ReflectionTestUtils.setField(config, "acquireTimeoutSeconds", acquireTimeout);
		ReflectionTestUtils.setField(config, "responseTimeoutSeconds", responseTimeout);
		return config;
	}

	@Test
	void pooledHttpClient_mtlsDisabled_createsClientWithoutSslBundle() throws Exception {
		HttpClientConfiguration config = buildConfig(100, 200, 45L, 28L);
		SslBundles sslBundles = mock(SslBundles.class);

		try (CloseableHttpClient client = config.pooledHttpClient(false, sslBundles)) {
			// mtlsEnabled=false: the SSLSocketFactory branch is skipped; client is non-null.
			assertThat(client).isNotNull();
		}
	}

	@Test
	void pooledHttpClient_mtlsEnabled_triesToLoadSslBundle() {
		HttpClientConfiguration config = buildConfig(100, 200, 45L, 28L);
		// Use a real (minimal) SslBundles mock that throws on unknown bundle name —
		// this exercises the mtlsEnabled=true branch (the SSL-socket-factory code path).
		SslBundles sslBundles = mock(SslBundles.class);
		given(sslBundles.getBundle("tweebyte")).willThrow(new NoSuchSslBundleException("tweebyte", "not configured"));

		assertThatThrownBy(() -> config.pooledHttpClient(true, sslBundles))
			.isInstanceOf(NoSuchSslBundleException.class);
	}

	@Test
	void pooledRestClientCustomizer_isNotNull() throws Exception {
		HttpClientConfiguration config = buildConfig(100, 200, 45L, 28L);
		SslBundles sslBundles = mock(SslBundles.class);

		try (CloseableHttpClient client = config.pooledHttpClient(false, sslBundles)) {
			var customizer = config.pooledRestClientCustomizer(client);
			assertThat(customizer).isNotNull();
		}
	}

}
