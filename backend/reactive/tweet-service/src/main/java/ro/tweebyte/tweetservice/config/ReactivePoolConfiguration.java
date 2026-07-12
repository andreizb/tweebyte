/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

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
 * Reactive tweet-service transport and R2DBC-pool tuning.
 *
 * <p>
 * Mirrors the user-service and interaction-service configurations. Without this class,
 * tweet-service was the only reactive service running r2dbc-pool's acquire/release on the
 * default Netty colocation path, which serialised the connection handoff onto one or two
 * event loops and capped reactive throughput well below what the unbottlenecked stack
 * should reach.
 *
 * <p>
 * The benchmark profile enables {@link #disableR2dbcLoopColocation()}
 * ({@code app.r2dbc.disable-colocation=true}) to fan that connection work across all
 * event-loop workers. The benchmark overlay also enables
 * {@link #r2dbcPoolAcquisitionScheduler} through
 * {@code app.r2dbc.pool.acquisition-scheduler.enabled=true}.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class ReactivePoolConfiguration {

	// r2dbc-postgresql exposes its LOOP_RESOURCES option key under the
	// camelCase string "loopResources". Recreate the Option here at compile
	// time rather than widening the postgresql driver dependency to
	// compile-scope just for this one constant.
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
	// parking indefinitely.
	@Value("${app.http.downstream.response-timeout-seconds:28}")
	private long responseTimeoutSeconds;

	// WebClient buffers each downstream response fully in memory before decoding; its
	// framework-default cap is 256 KiB. The async stack's blocking RestClient has no analogue
	// — it reads the full body unbounded — so this is a reactive-only parity knob (like
	// disableR2dbcLoopColocation). The consolidated interaction-enrichment response for a full
	// 1000-tweet page exceeds 256 KiB, so raise the cap to 16 MiB: reactive must be able to
	// receive the same large responses the async RestClient already accepts.
	@Value("${app.http.downstream.max-in-memory-bytes:16777216}")
	private int maxInMemoryBytes;

	private static final String SSL_BUNDLE = "tweebyte";

	/**
	 * Programmatic reactive transaction boundary for the tweet-update reconcile. Spring
	 * Boot's R2DBC auto-config already supplies the {@link ReactiveTransactionManager};
	 * wrapping only the load + save + reconcile Mono with this operator (rather than
	 * {@code @Transactional} on the whole method) keeps mention-username resolution — a
	 * user-service round-trip — outside the transaction so the pooled DB connection is not
	 * held across it.
	 * @param txManager the reactive transaction manager supplied by R2DBC auto-config
	 * @return a transactional operator over that manager
	 */
	@Bean
	public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
		return TransactionalOperator.create(txManager);
	}

	/**
	 * Shared Reactor Netty connection pool for the downstream WebClient fan-out (UserClient
	 * user summaries, InteractionClient counts / top replies / followed ids). A single pool
	 * sized per remote host is the reactive mirror of the async stack's one pooled Apache
	 * HttpClient connection manager.
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
	 * builder transparently shares the pool — the reactive mirror of async's single
	 * {@code RestClientCustomizer}.
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
	 * Dedicated scheduler for r2dbc-pool acquire/release handoff. Gated by
	 * {@code app.r2dbc.pool.acquisition-scheduler.enabled=true} so it can be A/B-toggled
	 * per environment.
	 * @param configuredWorkerCount worker thread count from configuration, or {@code 0}
	 * to derive the default from the available processor count
	 * @return the parallel scheduler used for pool acquisition handoff
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
	 * scheduler into r2dbc-pool's {@code acquisitionScheduler(...)} hook. Gated on the
	 * same property as the scheduler bean: when off, this factory is not registered and
	 * Spring Boot's default {@code
	 * ConnectionFactory} bean is used unchanged.
	 * @param properties the Spring Boot R2DBC connection and pool properties
	 * @param customizers connection-factory-options customizers contributed by other
	 * beans
	 * @param acquisitionScheduler the dedicated scheduler for pool acquisition handoff
	 * @return a connection pool wired to the dedicated acquisition scheduler
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
	 * @return a customizer that replaces r2dbc-postgresql's loop resources with a fan-out
	 * (non-colocating) configuration
	 */
	@Bean
	@ConditionalOnProperty(prefix = "app.r2dbc", name = "disable-colocation", havingValue = "true")
	public ConnectionFactoryOptionsBuilderCustomizer disableR2dbcLoopColocation() {
		int workerCount = Math.max(Runtime.getRuntime().availableProcessors(), 4);
		LoopResources fanOutLoops = LoopResources.create("tweet-r2dbc-loop", -1, // selectCount:
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
	 * Mirrors the user-service and interaction-service Netty server tuning.
	 * SO_BACKLOG=5000 raises the kernel TCP accept backlog (default 128 is too low for
	 * k6/JMeter burst opens at ramp). Write-buffer watermarks smooth out backpressure on
	 * the server-write path. TCP_NODELAY disables Nagle's algorithm so small responses
	 * don't wait for batching.
	 * @param nettyServerSoBacklog kernel TCP accept backlog (SO_BACKLOG)
	 * @param nettyServerWriteBufferLowWaterMark low watermark for the server write buffer
	 * @param nettyServerWriteBufferHighWaterMark high watermark for the server write
	 * buffer
	 * @param nettyServerTcpNoDelay whether to disable Nagle's algorithm (TCP_NODELAY)
	 * @return a Netty web-server factory with the benchmark transport tuning applied
	 */
	@Bean
	@ConditionalOnProperty(prefix = "app.netty.server", name = "enabled", havingValue = "true")
	NettyReactiveWebServerFactory nettyFactory(@Value("${app.netty.server.so-backlog:5000}") int nettyServerSoBacklog,
			@Value("${app.netty.server.write-buffer-low-water-mark:65536}") int nettyServerWriteBufferLowWaterMark,
			@Value("${app.netty.server.write-buffer-high-water-mark:131072}") int nettyServerWriteBufferHighWaterMark,
			@Value("${app.netty.server.tcp-no-delay:true}") boolean nettyServerTcpNoDelay) {
		NettyReactiveWebServerFactory f = new NettyReactiveWebServerFactory();
		f.addServerCustomizers(server -> server.option(ChannelOption.SO_BACKLOG, nettyServerSoBacklog)

			.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK,
					new WriteBufferWaterMark(nettyServerWriteBufferLowWaterMark, nettyServerWriteBufferHighWaterMark))

			.childOption(ChannelOption.TCP_NODELAY, nettyServerTcpNoDelay));
		return f;
	}

}
