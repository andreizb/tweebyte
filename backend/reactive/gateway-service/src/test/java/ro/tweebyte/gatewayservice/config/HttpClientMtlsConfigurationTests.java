/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.test.context.TestPropertySource;
import reactor.netty.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Wires the east-west mTLS customizer with {@code app.mtls.enabled=true} and a real
 * (self-signed) PKCS12 SSL bundle, so the {@link HttpClientMtlsConfiguration} bean is built
 * (it is absent in the other suites, which do not define the {@code tweebyte} bundle).
 * Verifies the {@link HttpClientCustomizer} bean is present and that applying it to a base
 * {@link HttpClient} secures the client without error — exercising the customizer and the
 * inner {@code sslContext} configuration lambda against the bundle's key/trust managers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = { "app.mtls.enabled=true",
		"spring.ssl.bundle.jks.tweebyte.keystore.location=classpath:ssl/test-keystore.p12",
		"spring.ssl.bundle.jks.tweebyte.keystore.password=changeit",
		"spring.ssl.bundle.jks.tweebyte.keystore.type=PKCS12",
		"spring.ssl.bundle.jks.tweebyte.key.password=changeit",
		"spring.ssl.bundle.jks.tweebyte.truststore.location=classpath:ssl/test-keystore.p12",
		"spring.ssl.bundle.jks.tweebyte.truststore.password=changeit",
		"spring.ssl.bundle.jks.tweebyte.truststore.type=PKCS12" })
class HttpClientMtlsConfigurationTests {

	@Autowired
	private HttpClientCustomizer mtlsHttpClientCustomizer;

	@Test
	void customizerBeanIsBuiltWhenMtlsEnabled() {
		assertThat(this.mtlsHttpClientCustomizer)
			.as("the east-west mTLS customizer must be wired when app.mtls.enabled=true")
			.isNotNull();
	}

	@Test
	void customizerSecuresTheOutboundHttpClient() {
		// Applying the customizer runs both the httpClient.secure(...) lambda and the inner
		// spec.sslContext(...) lambda built from the bundle's key/trust managers.
		assertThatCode(() -> {
			HttpClient secured = this.mtlsHttpClientCustomizer.customize(HttpClient.create());
			assertThat(secured).as("the secured client must be returned").isNotNull();
		}).doesNotThrowAnyException();
	}

}
