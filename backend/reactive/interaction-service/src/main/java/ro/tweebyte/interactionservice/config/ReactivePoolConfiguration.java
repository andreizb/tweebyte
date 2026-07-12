/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import java.time.Duration;
import java.util.Map;

import javax.net.ssl.SSLException;

import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Option;
import io.r2dbc.spi.ValidationDepth;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

/**
 * Reactive interaction-service transport and R2DBC-pool tuning.
 *
 * <p>
 * The benchmark profile enables {@link #disableR2dbcLoopColocation()}
 * ({@code app.r2dbc.disable-colocation=true}) to fan reactor-pool's connection work across
 * all event-loop workers instead of pinning it to one or two loops. The benchmark overlay
 * also enables {@link #r2dbcPoolAcquisitionScheduler} through
 * {@code app.r2dbc.pool.acquisition-scheduler.enabled=true}.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class ReactivePoolConfiguration {

	// r2dbc-postgresql exposes its LOOP_RESOURCES option key under the
	// camelCase string "loopResources" — see
	// io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider#LOOP_RESOURCES.
	// Recreate the Option here at compile time rather than widening the
	// postgresql driver dependency to compile-scope just for this constant.
	private static final Option<LoopResources> R2DBC_LOOP_RESOURCES = Option.valueOf("loopResources");

	// Per-host outbound connection cap for the downstream WebClient fan-out, declared in
	// application.properties (app.http.downstream.*, default 1000) so the measured fan-out
	// config is receipt-visible. Reactor Netty pools per remote host, so this equals the
	// async stack's Apache HttpClient maxConnPerRoute (same 1000 default) — the two
	// transports cap concurrent connections per downstream identically.
	// PENDING_ACQUIRE_MAX_COUNT=-1 leaves the lease-wait queue unbounded so bursts queue
	// (up to PENDING_ACQUIRE_TIMEOUT, 45s default and 3600s under the benchmark overlay)
	// rather than fail fast — matching the async Apache HttpClient connection-request
	// queue, which is itself unbounded.
	@Value("${app.http.downstream.max-connections:1000}")
	private int maxConnectionsPerHost;

	@Value("${app.http.downstream.pending-acquire-max-count:-1}")
	private int pendingAcquireMaxCount;

	@Value("${app.http.downstream.acquire-timeout-seconds:45}")
	private long acquireTimeoutSeconds;

	// Response timeout — a safety net symmetric with async's RestClient read timeout. Far
	// above any healthy-cell latency (28s), so it never fires in a clean run; it only caps a
	// pathological slow downstream so a stalled call frees its pooled connection instead of
	// parking indefinitely. Applied via the shared customizer to every downstream WebClient.
	@Value("${app.http.downstream.response-timeout-seconds:28}")
	private long responseTimeoutSeconds;

	// WebClient buffers each downstream response fully in memory before decoding; its
	// framework-default cap is 256 KiB. The async stack's blocking RestClient has no analogue
	// — it reads the full body unbounded — so this is a reactive-only parity knob (like
	// disableR2dbcLoopColocation), raised to 16 MiB so reactive can receive the same large
	// downstream responses (tweet summaries / popular hashtags / user summaries) the async
	// RestClient already accepts. Applied via the shared customizer to every downstream WebClient.
	@Value("${app.http.downstream.max-in-memory-bytes:16777216}")
	private int maxInMemoryBytes;

	private static final String SSL_BUNDLE = "tweebyte";

	/**
	 * Programmatic reactive transaction boundary for like/unlike operations. Spring Boot's
	 * R2DBC auto-config supplies the {@link ReactiveTransactionManager}; wrapping only the
	 * find + delete Mono with this operator keeps the transaction scope minimal.
	 * @param txManager the reactive transaction manager supplied by R2DBC auto-config
	 * @return a transactional operator over that manager
	 */
	@Bean
	public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
		return TransactionalOperator.create(txManager);
	}

	/**
	 * Shared Reactor Netty connection pool for the downstream WebClient fan-out
	 * (TweetClient tweet summaries / popular hashtags, UserClient user summaries). A single
	 * pool sized per remote host is the reactive mirror of the async stack's one pooled
	 * Apache HttpClient connection manager.
	 * @return the shared downstream connection provider
	 */
	@Bean(destroyMethod = "dispose")
	public ConnectionProvider downstreamConnectionProvider() {
		return ConnectionProvider.builder("downstream")
			.maxConnections(this.maxConnectionsPerHost)
			.pendingAcquireMaxCount(this.pendingAcquireMaxCount)
			.pendingAcquireTimeout(Duration.ofSeconds(this.acquireTimeoutSeconds))
			.build();
	}

	/**
	 * Binds the shared {@link #downstreamConnectionProvider()} to every WebClient built from
	 * the autoconfigured {@code WebClient.Builder}, so each downstream client injecting that
	 * builder (TweetClient, UserClient) transparently shares the pool, response timeout, and
	 * in-memory buffer cap — the reactive mirror of async's single {@code RestClientCustomizer}.
	 * @param downstreamConnectionProvider the shared downstream connection provider
	 * @param mtlsEnabled whether to present this service's client cert on outbound calls
	 * @param sslBundles the SSL bundle registry holding the shared {@code tweebyte} identity
	 * @return a customizer that installs the pooled Reactor Netty connector
	 */
	@Bean
	public WebClientCustomizer pooledWebClientCustomizer(ConnectionProvider downstreamConnectionProvider,
			@Value("${app.mtls.enabled:false}") boolean mtlsEnabled, SslBundles sslBundles) {
		HttpClient base = HttpClient.create(downstreamConnectionProvider).option(ChannelOption.TCP_NODELAY, true)
			.responseTimeout(Duration.ofSeconds(this.responseTimeoutSeconds));
		// East-west mTLS (prod / functional-equivalence): present this service's client cert
		// and trust the dev CA on every outbound WebClient call. Off in benchmark
		// (app.mtls.enabled=false), so the connector stays plaintext and the measured fan-out
		// pays no TLS cost.
		HttpClient httpClient = mtlsEnabled
				? base.secure(spec -> spec.sslContext(clientSslContext(sslBundles)))
				: base;
		return builder -> builder.clientConnector(new ReactorClientHttpConnector(httpClient))
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(this.maxInMemoryBytes));
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

	/**
	 * Dedicated scheduler for r2dbc-pool acquire/release handoff.
	 *
	 * Mirrors the user-service bean. Under follow-create at conc≥200 we observed deep
	 * pending-acquire queues on reactor-pool's default handoff path while the pool still
	 * reported idle connections — symptom of acquire/drain contention on the event-loop
	 * threads. A dedicated parallel scheduler moves that handoff off the Netty loops onto
	 * a small pool sized by core count.
	 *
	 * Gated by {@code app.r2dbc.pool.acquisition-scheduler.enabled=true} so it can be
	 * A/B-toggled per environment. When the property is unset the bean is not registered
	 * and r2dbc-pool falls back to its default scheduler — guaranteed identical to
	 * pre-existing behaviour.
	 * @param configuredWorkerCount worker count from
	 * {@code app.r2dbc.pool.acquisition-scheduler.worker-count}; {@code 0} keeps the
	 * default of {@code max(cpus, 4)}
	 * @return a dedicated parallel scheduler for pool acquire/release handoff
	 */
	@Bean(name = "r2dbcPoolAcquisitionScheduler", destroyMethod = "dispose")
	@ConditionalOnProperty(prefix = "app.r2dbc.pool.acquisition-scheduler", name = "enabled", havingValue = "true")
	public Scheduler r2dbcPoolAcquisitionScheduler(
			@Value("${app.r2dbc.pool.acquisition-scheduler.worker-count:0}") int configuredWorkerCount) {
		// configuredWorkerCount=0 → keep prior default (max(cpus,4)).
		int workerCount = (configuredWorkerCount > 0) ? configuredWorkerCount
				: Math.max(Runtime.getRuntime().availableProcessors(), 4);
		return Schedulers.newParallel("r2dbc-pool-acquire", workerCount, true);
	}

	/**
	 * Benchmark-only ConnectionPool override that wires the dedicated acquisition
	 * scheduler into r2dbc-pool's {@code acquisitionScheduler(...)} hook.
	 *
	 * Spring Boot's autoconfig exposes pool size/timeout knobs but not the
	 * acquisitionScheduler hook, so we have to construct the {@code ConnectionPool}
	 * ourselves. The factory builder logic is identical to user-service's — Spring's
	 * {@code R2dbcProperties} + {@code ConnectionFactoryBuilder} + every registered
	 * {@link ConnectionFactoryOptionsBuilderCustomizer} (so
	 * {@link #disableR2dbcLoopColocation()} still applies when both properties are
	 * enabled together).
	 *
	 * Gated on the same property as the scheduler bean: when off, this factory is not
	 * registered and Spring Boot's default {@code ConnectionFactory} bean is used
	 * unchanged.
	 * @param properties the Spring Boot R2DBC properties used to build the connection
	 * factory
	 * @param customizers registered connection-factory-options customizers applied to the
	 * built factory
	 * @param acquisitionScheduler dedicated scheduler wired into the pool's acquisition
	 * hook
	 * @return a connection pool configured with the dedicated acquisition scheduler
	 */
	@Bean
	@ConditionalOnProperty(prefix = "app.r2dbc.pool.acquisition-scheduler", name = "enabled", havingValue = "true")
	public ConnectionPool connectionFactory(R2dbcProperties properties,
			ObjectProvider<ConnectionFactoryOptionsBuilderCustomizer> customizers,
			@Qualifier("r2dbcPoolAcquisitionScheduler") Scheduler acquisitionScheduler) {

		ConnectionFactoryBuilder factoryBuilder = ConnectionFactoryBuilder.withUrl(properties.getUrl());
		if (properties.getUsername() != null) {
			factoryBuilder.username(properties.getUsername());
		}
		if (properties.getPassword() != null) {
			factoryBuilder.password(properties.getPassword());
		}
		factoryBuilder.configure(options -> {
			boolean sslDisabled = "DISABLE".equalsIgnoreCase(properties.getProperties().get("sslMode"));
			for (Map.Entry<String, String> entry : properties.getProperties().entrySet()) {
				// r2dbc-postgresql validates that sslRootCert/sslCert/sslKey point to existing
				// files even when SSL is off; the benchmark overlay sets sslMode=DISABLE, so skip
				// those file-path options to let host-JVM benchmark boots succeed without the prod
				// certs at /etc/tweebyte/tls (which exist only inside the service containers).
				String key = entry.getKey();
				if (sslDisabled && (key.equals("sslRootCert") || key.equals("sslCert") || key.equals("sslKey"))) {
					continue;
				}
				options.option(Option.valueOf(key), entry.getValue());
			}
			customizers.orderedStream().forEach(customizer -> customizer.customize(options));
		});

		ConnectionFactory delegate = factoryBuilder.build();
		ConnectionPoolConfiguration.Builder pool = ConnectionPoolConfiguration.builder(delegate);
		applyPoolProperties(properties.getPool(), pool);
		pool.customizer(builder -> builder.acquisitionScheduler(acquisitionScheduler));
		return new ConnectionPool(pool.build());
	}

	private static void applyPoolProperties(R2dbcProperties.Pool properties, ConnectionPoolConfiguration.Builder pool) {
		pool.initialSize(properties.getInitialSize());
		pool.maxSize(properties.getMaxSize());
		pool.minIdle(properties.getMinIdle());
		applyIfPresent(properties.getMaxIdleTime(), pool::maxIdleTime);
		applyIfPresent(properties.getMaxLifeTime(), pool::maxLifeTime);
		applyIfPresent(properties.getMaxAcquireTime(), pool::maxAcquireTime);
		applyIfPresent(properties.getMaxCreateConnectionTime(), pool::maxCreateConnectionTime);
		applyIfPresent(properties.getMaxValidationTime(), pool::maxValidationTime);
		ValidationDepth validationDepth = properties.getValidationDepth();
		if (validationDepth != null) {
			pool.validationDepth(validationDepth);
		}
		String validationQuery = properties.getValidationQuery();
		if (validationQuery != null && !validationQuery.isBlank()) {
			pool.validationQuery(validationQuery);
		}
	}

	private static void applyIfPresent(Duration duration, java.util.function.Consumer<Duration> consumer) {
		if (duration != null) {
			consumer.accept(duration);
		}
	}

	/**
	 * The colocation fix, enabled by the benchmark profile via
	 * {@code app.r2dbc.disable-colocation=true} to fan connection work across all
	 * event-loop workers instead of pinning it to one or two loops. Gated by
	 * {@link ConditionalOnProperty}, so when the property is unset (e.g. the default
	 * profile) the bean is not registered and r2dbc-pool keeps its default colocation.
	 * @return a customizer that swaps in fan-out loop resources to disable r2dbc loop
	 * colocation
	 */
	@Bean
	@ConditionalOnProperty(prefix = "app.r2dbc", name = "disable-colocation", havingValue = "true")
	public ConnectionFactoryOptionsBuilderCustomizer disableR2dbcLoopColocation() {
		int workerCount = Math.max(Runtime.getRuntime().availableProcessors(), 4);
		LoopResources fanOutLoops = LoopResources.create("interaction-r2dbc-loop", -1, // selectCount:
																						// -1
																						// =
																						// reactor-netty
																						// default
				workerCount, // worker threads, one per CPU core
				true, // daemon: shut down with the JVM
				false // colocate: false = fan work across workers
		);
		return builder -> builder.option(R2DBC_LOOP_RESOURCES, fanOutLoops);
	}

	/**
	 * Mirrors the user-service Netty server factory tuning (see
	 * {@code reactive/user-service/.../ReactivePoolConfiguration.java}).
	 *
	 * <p>
	 * {@code SO_BACKLOG=5000} sets the kernel TCP accept backlog (default 128 is too low
	 * for k6 burst opens at ramp).
	 *
	 * <p>
	 * {@code WRITE_BUFFER_WATER_MARK} caps per-channel outbound buffer pressure to 64 KB
	 * low / 128 KB high, matching the other reactive services. A larger 256 KB / 512 KB
	 * allowance (with {@code SO_SNDBUF=512K}) for multi-KB cache-hit JSON responses inflates
	 * committed heap at high concurrency without a stable rps win, so the smaller watermark
	 * is the default.
	 *
	 * <p>
	 * {@code TCP_NODELAY=true} disables Nagle's algorithm so small frames (cache-hit
	 * responses) flush immediately rather than coalescing into 40-200 ms send batches.
	 * Both knobs apply identically to user-service and
	 * interaction-service in the reactive stack, and they have no JDBC/Tomcat equivalent
	 * for the async stack (Tomcat manages its own write buffers via its NIO connector
	 * defaults).
	 * @param nettyServerSoBacklog kernel TCP accept backlog
	 * @param nettyServerWriteBufferLowWaterMark per-channel outbound buffer low water
	 * mark in bytes
	 * @param nettyServerWriteBufferHighWaterMark per-channel outbound buffer high water
	 * mark in bytes
	 * @param nettyServerTcpNoDelay whether to disable Nagle's algorithm
	 * @return a Netty reactive web server factory configured with the tuned channel
	 * options
	 */
	@Bean
	@ConditionalOnProperty(prefix = "app.netty.server", name = "enabled", havingValue = "true")
	NettyReactiveWebServerFactory nettyFactory(@Value("${app.netty.server.so-backlog:5000}") int nettyServerSoBacklog,
			@Value("${app.netty.server.write-buffer-low-water-mark:65536}") int nettyServerWriteBufferLowWaterMark,
			@Value("${app.netty.server.write-buffer-high-water-mark:131072}") int nettyServerWriteBufferHighWaterMark,
			@Value("${app.netty.server.tcp-no-delay:true}") boolean nettyServerTcpNoDelay) {
		NettyReactiveWebServerFactory f = new NettyReactiveWebServerFactory();
		f.addServerCustomizers(server -> server.option(ChannelOption.SO_BACKLOG, nettyServerSoBacklog)
			// Aligned with user-service + tweet-service (64K/128K): a larger
			// 256K/512K outbound buffer (with SO_SNDBUF=512K) suits following-cache's
			// multi-KB JSON responses but grows reactive heap at high concurrency, so
			// the smaller watermark is the default. Values live in
			// application.properties under app.netty.server.*.
			.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
					new WriteBufferWaterMark(nettyServerWriteBufferLowWaterMark, nettyServerWriteBufferHighWaterMark))

			.childOption(ChannelOption.TCP_NODELAY, nettyServerTcpNoDelay));
		return f;
	}

}
