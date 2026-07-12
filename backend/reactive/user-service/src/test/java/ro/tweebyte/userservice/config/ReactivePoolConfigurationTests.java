/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.scheduler.Scheduler;
import reactor.netty.resources.ConnectionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ReactivePoolConfiguration} — exercises all directly-testable
 * @Bean methods and their conditional branches (mTLS enabled/disabled, etc.).
 *
 * <p>The @ConditionalOnProperty-gated beans ({@code connectionFactory},
 * {@code disableR2dbcLoopColocation}, {@code nettyFactory},
 * {@code r2dbcPoolAcquisitionScheduler}) are exercised directly by calling the bean
 * methods without activating any Spring context, bypassing the condition evaluation.
 * This drives the method bodies (and their internal branches) through JaCoCo.
 */
class ReactivePoolConfigurationTests {

	private final ReactivePoolConfigurationTests.TestableConfig config = new TestableConfig();

	private Scheduler createdScheduler;

	@AfterEach
	void tearDown() {
		if (this.createdScheduler != null) {
			this.createdScheduler.dispose();
		}
	}

	// --- readScheduler ---

	@Test
	void readScheduler_returnsNonNullScheduler() {
		this.createdScheduler = this.config.readScheduler();
		assertThat(this.createdScheduler).isNotNull();
	}

	// --- downstreamConnectionProvider ---

	@Test
	void downstreamConnectionProvider_buildsProvider() {
		ConnectionProvider provider = this.config.downstreamConnectionProvider();
		assertThat(provider).isNotNull();
		provider.dispose();
	}

	// --- pooledWebClientCustomizer: mTLS disabled branch ---

	@Test
	void pooledWebClientCustomizer_mtlsDisabled_doesNotRequireSslBundle() {
		ConnectionProvider provider = this.config.downstreamConnectionProvider();
		SslBundles sslBundles = mock(SslBundles.class);

		var customizer = this.config.pooledWebClientCustomizer(provider, false, sslBundles);
		assertThat(customizer).isNotNull();
		provider.dispose();
	}

	// --- pooledWebClientCustomizer: mTLS enabled branch ---

	@Test
	void pooledWebClientCustomizer_mtlsEnabled_triesToLoadSslBundle() {
		ConnectionProvider provider = this.config.downstreamConnectionProvider();
		SslBundles sslBundles = mock(SslBundles.class);
		given(sslBundles.getBundle("tweebyte")).willThrow(new NoSuchSslBundleException("tweebyte", "not configured"));

		assertThatThrownBy(() -> this.config.pooledWebClientCustomizer(provider, true, sslBundles))
			.isInstanceOf(NoSuchSslBundleException.class);
		provider.dispose();
	}

	// --- r2dbcPoolAcquisitionScheduler ---

	@Test
	void r2dbcPoolAcquisitionScheduler_returnsParallelScheduler() {
		Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		assertThat(scheduler).isNotNull();
		scheduler.dispose();
	}

	// --- disableR2dbcLoopColocation ---

	@Test
	void disableR2dbcLoopColocation_returnsCustomizer() {
		var customizer = this.config.disableR2dbcLoopColocation();
		assertThat(customizer).isNotNull();
	}

	// --- nettyFactory ---

	@Test
	void nettyFactory_returnsConfiguredFactory() {
		var factory = this.config.nettyFactory(5000, 65536, 131072, true);
		assertThat(factory).isNotNull();
	}

	// --- Helper: subclass with defaults injected via @Value fields set by reflection ---

	/**
	 * Subclass that pre-wires all @Value fields so we can call bean methods without a
	 * Spring context and without specifying property sources.
	 */
	static class TestableConfig extends ReactivePoolConfiguration {

		TestableConfig() {
			ReflectionTestUtils.setField(this, "readSchedulerPoolSize", 10);
			ReflectionTestUtils.setField(this, "readSchedulerQueueCapacity", 1000);
			ReflectionTestUtils.setField(this, "maxConnectionsPerHost", 100);
			ReflectionTestUtils.setField(this, "pendingAcquireMaxCount", -1);
			ReflectionTestUtils.setField(this, "acquireTimeoutSeconds", 10L);
			ReflectionTestUtils.setField(this, "responseTimeoutSeconds", 28L);
			ReflectionTestUtils.setField(this, "maxInMemoryBytes", 16777216);
		}

	}

}
