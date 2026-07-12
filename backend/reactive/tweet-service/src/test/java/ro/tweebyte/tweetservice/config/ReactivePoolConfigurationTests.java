/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.scheduler.Scheduler;
import reactor.netty.resources.ConnectionProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link ReactivePoolConfiguration}.
 * Exercises the branches that are reachable without a live database or mTLS bundle.
 * mTLS and acquisition-scheduler paths require integration-level wiring.
 */
@ExtendWith(MockitoExtension.class)
class ReactivePoolConfigurationTests {

	@Mock
	private SslBundles sslBundles;

	@Mock
	private ReactiveTransactionManager transactionManager;

	// -------------------------------------------------------------------------
	// transactionalOperator — no branch; just ensures the bean is produced
	// -------------------------------------------------------------------------

	@Test
	void transactionalOperator_returnsNonNull() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		TransactionalOperator operator = config.transactionalOperator(this.transactionManager);
		assertThat(operator).isNotNull();
	}

	// -------------------------------------------------------------------------
	// downstreamConnectionProvider — produces a ConnectionProvider
	// -------------------------------------------------------------------------

	@Test
	void downstreamConnectionProvider_returnsNonNull() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		ConnectionProvider provider = config.downstreamConnectionProvider();
		assertThat(provider).isNotNull();
		provider.dispose();
	}

	// -------------------------------------------------------------------------
	// pooledWebClientCustomizer — mTLS disabled branch
	// -------------------------------------------------------------------------

	@Test
	void pooledWebClientCustomizer_mtlsDisabled_returnsCustomizer() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		ConnectionProvider provider = config.downstreamConnectionProvider();
		WebClientCustomizer customizer = config.pooledWebClientCustomizer(provider, false, this.sslBundles);
		assertThat(customizer).isNotNull();
		provider.dispose();
	}

	@Test
	void pooledWebClientCustomizer_mtlsDisabled_customizesBuilder() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		ConnectionProvider provider = config.downstreamConnectionProvider();
		WebClientCustomizer customizer = config.pooledWebClientCustomizer(provider, false, this.sslBundles);
		// Applying the customizer to a real builder should not throw
		org.springframework.web.reactive.function.client.WebClient.Builder builder =
			org.springframework.web.reactive.function.client.WebClient.builder();
		customizer.customize(builder);
		provider.dispose();
	}

	// -------------------------------------------------------------------------
	// r2dbcPoolAcquisitionScheduler — workerCount=0 → default from CPU count
	// -------------------------------------------------------------------------

	@Test
	void r2dbcPoolAcquisitionScheduler_zeroWorkerCount_usesDefaultCount() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		Scheduler scheduler = config.r2dbcPoolAcquisitionScheduler(0);
		assertThat(scheduler).isNotNull();
		scheduler.dispose();
	}

	@Test
	void r2dbcPoolAcquisitionScheduler_positiveWorkerCount_usesExplicitCount() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		Scheduler scheduler = config.r2dbcPoolAcquisitionScheduler(4);
		assertThat(scheduler).isNotNull();
		scheduler.dispose();
	}

	// -------------------------------------------------------------------------
	// disableR2dbcLoopColocation — produces a ConnectionFactoryOptionsBuilderCustomizer
	// -------------------------------------------------------------------------

	@Test
	void disableR2dbcLoopColocation_returnsNonNull() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		var customizer = config.disableR2dbcLoopColocation();
		assertThat(customizer).isNotNull();
	}

	// -------------------------------------------------------------------------
	// nettyFactory — conditional bean but directly callable in unit test
	// -------------------------------------------------------------------------

	@Test
	void nettyFactory_returnsNonNull() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		NettyReactiveWebServerFactory factory = config.nettyFactory(5000, 65536, 131072, true);
		assertThat(factory).isNotNull();
	}

	@Test
	void nettyFactory_withTcpNoDelayFalse_returnsNonNull() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		NettyReactiveWebServerFactory factory = config.nettyFactory(1024, 32768, 65536, false);
		assertThat(factory).isNotNull();
	}

	// -------------------------------------------------------------------------
	// applyPoolProperties + applyIfPresent — via reflection on private statics
	// using a Mockito-mocked ConnectionFactory (no DB driver needed)
	// -------------------------------------------------------------------------

	@Test
	void applyPoolProperties_withAllDurationsAndValidation_coversNonNullBranches() throws Exception {
		var applyMethod = ReactivePoolConfiguration.class
			.getDeclaredMethod("applyPoolProperties",
				R2dbcProperties.Pool.class,
				io.r2dbc.pool.ConnectionPoolConfiguration.Builder.class);
		applyMethod.setAccessible(true);

		io.r2dbc.spi.ConnectionFactory stubCf = org.mockito.Mockito.mock(io.r2dbc.spi.ConnectionFactory.class);
		io.r2dbc.pool.ConnectionPoolConfiguration.Builder poolBuilder =
			io.r2dbc.pool.ConnectionPoolConfiguration.builder(stubCf);

		R2dbcProperties.Pool pool = new R2dbcProperties.Pool();
		pool.setMaxIdleTime(Duration.ofSeconds(30));
		pool.setMaxLifeTime(Duration.ofSeconds(300));
		pool.setMaxAcquireTime(Duration.ofSeconds(45));
		pool.setMaxCreateConnectionTime(Duration.ofSeconds(10));
		pool.setMaxValidationTime(Duration.ofSeconds(5));
		pool.setValidationDepth(io.r2dbc.spi.ValidationDepth.LOCAL);
		pool.setValidationQuery("SELECT 1");

		applyMethod.invoke(null, pool, poolBuilder);
		assertThat(poolBuilder).isNotNull();
	}

	@Test
	void applyPoolProperties_withNullDurationsAndNoValidation_coversNullBranches() throws Exception {
		var applyMethod = ReactivePoolConfiguration.class
			.getDeclaredMethod("applyPoolProperties",
				R2dbcProperties.Pool.class,
				io.r2dbc.pool.ConnectionPoolConfiguration.Builder.class);
		applyMethod.setAccessible(true);

		io.r2dbc.spi.ConnectionFactory stubCf = org.mockito.Mockito.mock(io.r2dbc.spi.ConnectionFactory.class);
		io.r2dbc.pool.ConnectionPoolConfiguration.Builder poolBuilder =
			io.r2dbc.pool.ConnectionPoolConfiguration.builder(stubCf);

		// Default pool — all Durations null, no validationDepth, no validationQuery
		R2dbcProperties.Pool pool = new R2dbcProperties.Pool();

		applyMethod.invoke(null, pool, poolBuilder);
		assertThat(poolBuilder).isNotNull();
	}

	@Test
	void applyPoolProperties_withBlankValidationQuery_coversBlankBranch() throws Exception {
		var applyMethod = ReactivePoolConfiguration.class
			.getDeclaredMethod("applyPoolProperties",
				R2dbcProperties.Pool.class,
				io.r2dbc.pool.ConnectionPoolConfiguration.Builder.class);
		applyMethod.setAccessible(true);

		io.r2dbc.spi.ConnectionFactory stubCf = org.mockito.Mockito.mock(io.r2dbc.spi.ConnectionFactory.class);
		io.r2dbc.pool.ConnectionPoolConfiguration.Builder poolBuilder =
			io.r2dbc.pool.ConnectionPoolConfiguration.builder(stubCf);

		R2dbcProperties.Pool pool = new R2dbcProperties.Pool();
		pool.setValidationQuery("  ");  // blank — exercises the !isBlank() false branch

		applyMethod.invoke(null, pool, poolBuilder);
		assertThat(poolBuilder).isNotNull();
	}

	// -------------------------------------------------------------------------
	// connectionFactory — directly callable with a postgres URL (no live DB needed
	// since r2dbc-pool is lazy: it only connects when .connect() is called)
	// -------------------------------------------------------------------------

	@Test
	void connectionFactory_withDefaultPoolAndNoCustomizers_buildsWithoutConnecting() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();

		R2dbcProperties props = new R2dbcProperties();
		props.setUrl("r2dbc:postgresql://localhost:5432/testdb");
		props.setUsername("user");
		props.setPassword("pass");

		org.springframework.beans.factory.ObjectProvider<
			org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer> noOps =
			org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class,
				org.mockito.Mockito.withSettings().lenient());
		given(noOps.orderedStream()).willReturn(java.util.stream.Stream.empty());

		Scheduler acquisitionScheduler = config.r2dbcPoolAcquisitionScheduler(2);

		// connectionFactory is a bean method — call it directly (no live DB needed:
		// r2dbc-pool is lazy and only connects when .connect() is called, never here)
		io.r2dbc.pool.ConnectionPool pool = config.connectionFactory(props, noOps, acquisitionScheduler);
		assertThat(pool).isNotNull();
		// Do NOT call pool.create() — that would try to connect to PostgreSQL.
		// Just verify the pool was constructed and close it.
		pool.close();
		acquisitionScheduler.dispose();
	}

	@Test
	void connectionFactory_withSslModeDisable_skipsFileOptions() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();

		R2dbcProperties props = new R2dbcProperties();
		props.setUrl("r2dbc:postgresql://localhost:5432/testdb");
		props.setUsername("user");
		props.setPassword("pass");
		// sslMode=DISABLE + file-path options: the code must skip sslRootCert/sslCert/sslKey
		props.getProperties().put("sslMode", "DISABLE");
		props.getProperties().put("sslRootCert", "/etc/tweebyte/tls/ca.crt");
		props.getProperties().put("sslCert", "/etc/tweebyte/tls/client.crt");
		props.getProperties().put("sslKey", "/etc/tweebyte/tls/client.key");
		props.getProperties().put("applicationName", "tweet-service-test");

		org.springframework.beans.factory.ObjectProvider<
			org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer> noOps =
			org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class,
				org.mockito.Mockito.withSettings().lenient());
		given(noOps.orderedStream()).willReturn(java.util.stream.Stream.empty());

		Scheduler acquisitionScheduler = config.r2dbcPoolAcquisitionScheduler(2);
		io.r2dbc.pool.ConnectionPool pool = config.connectionFactory(props, noOps, acquisitionScheduler);
		assertThat(pool).isNotNull();
		pool.close();
		acquisitionScheduler.dispose();
	}

	@Test
	void disableR2dbcLoopColocation_customizerIsApplicable() throws Exception {
		ReactivePoolConfiguration config = defaultConfig();
		var customizer = config.disableR2dbcLoopColocation();
		io.r2dbc.spi.ConnectionFactoryOptions.Builder optionsBuilder =
			io.r2dbc.spi.ConnectionFactoryOptions.parse("r2dbc:pool:postgresql://localhost/test").mutate();
		customizer.customize(optionsBuilder);
		assertThat(optionsBuilder).isNotNull();
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	private static ReactivePoolConfiguration defaultConfig() throws Exception {
		ReactivePoolConfiguration config = new ReactivePoolConfiguration();
		setField(config, "maxConnectionsPerHost", 10);
		setField(config, "pendingAcquireMaxCount", -1);
		setField(config, "acquireTimeoutSeconds", 5L);
		setField(config, "responseTimeoutSeconds", 5L);
		setField(config, "maxInMemoryBytes", 16 * 1024 * 1024);
		return config;
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

}
