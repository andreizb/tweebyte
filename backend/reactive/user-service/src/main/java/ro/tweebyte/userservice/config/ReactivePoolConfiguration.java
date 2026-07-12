/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

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
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

@Configuration
public class ReactivePoolConfiguration {

	@Value("${app.concurrency.user.read-scheduler.pool-size:1000}")
	private int readSchedulerPoolSize;

	@Value("${app.concurrency.user.read-scheduler.queue-capacity:100000}")
	private int readSchedulerQueueCapacity;

	@Bean(name = "readScheduler")
	public Scheduler readScheduler() {
		return Schedulers.newBoundedElastic(this.readSchedulerPoolSize, this.readSchedulerQueueCapacity,
				"blocking-read", 60, true);
	}

	/**
	 * Disable r2dbc-pool's connection-to-event-loop colocation.
	 *
	 * By default reactor-pool uses {@code LoopResources.colocate(name)} which pins each
	 * newly-acquired R2DBC connection to whichever event-loop thread first asked for it.
	 * Under sustained load with our pool size, most connections end up colocated on 1-2
	 * worker threads → DB queries serialize through a single core even when the pool has
	 * 60 connections and the host has 16 logical CPUs. Symptom: throughput plateaus at
	 * conc=100 well below CPU saturation (~25% CPU spread thinly across 16 NIO threads).
	 * Disabling colocation fans the work across all workers and unblocks the
	 * conc=100→1000 scaling path.
	 *
	 * The customizer applies to the underlying r2dbc-postgresql {@code ConnectionFactory}
	 * before r2dbc-pool wraps it, so the option propagates correctly.
	 *
	 * Sources:
	 * https://piotrd.hashnode.dev/javas-reactive-connection-pooling-performance-caveat
	 * https://github.com/r2dbc/r2dbc-pool/issues/190
	 * https://github.com/reactor/reactor-netty/issues/2781
	 */
	// Mirrors PostgresqlConnectionFactoryProvider#LOOP_RESOURCES, the driver's
	// Option<LoopResources> keyed on "loopResources". The driver is runtime-scoped in
	// pom.xml, so we recreate the Option here rather than widen the scope to compile for
	// one constant — the resolved value is identical either way.
	private static final Option<LoopResources> R2DBC_LOOP_RESOURCES = Option.valueOf("loopResources");

	// Per-host outbound connection cap for the shared Reactor Netty pool, declared in
	// application.properties (app.http.downstream.*, default 1000) so the measured fan-out
	// config is receipt-visible. Mirrors the async stack's Apache HttpClient 5
	// maxConnPerRoute (same 1000 default) so both transports admit the same in-flight fan-out
	// per downstream. pendingAcquireMaxCount=-1 leaves the lease-wait queue unbounded so
	// bursts past the live connections queue (up to pendingAcquireTimeout, 45s default and
	// 3600s under the benchmark overlay) rather than fail fast — matching HC5's
	// connection-request queue, which is itself unbounded, so a starved caller waits the same
	// way on both stacks.
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
	// disableR2dbcLoopColocation), raised to 16 MiB so reactive can receive the same large
	// consolidated interaction responses the async RestClient already accepts.
	@Value("${app.http.downstream.max-in-memory-bytes:16777216}")
	private int maxInMemoryBytes;

	private static final String SSL_BUNDLE = "tweebyte";

	@Bean(destroyMethod = "dispose")
	public ConnectionProvider downstreamConnectionProvider() {
		return ConnectionProvider.builder("downstream")
			.maxConnections(this.maxConnectionsPerHost)
			.pendingAcquireMaxCount(this.pendingAcquireMaxCount)
			.pendingAcquireTimeout(Duration.ofSeconds(this.acquireTimeoutSeconds))
			.build();
	}

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

	@Bean(name = "r2dbcPoolAcquisitionScheduler", destroyMethod = "dispose")
	@ConditionalOnProperty(prefix = "app.r2dbc.pool.acquisition-scheduler", name = "enabled", havingValue = "true")
	public Scheduler r2dbcPoolAcquisitionScheduler() {
		int workerCount = Math.max(Runtime.getRuntime().availableProcessors(), 4);
		return Schedulers.newParallel("r2dbc-pool-acquire", workerCount, true);
	}

	/**
	 * Benchmark-only pool builder override.
	 *
	 * Spring Boot exposes r2dbc-pool's size/time/validation knobs, but not reactor-pool's
	 * acquisitionScheduler hook. Under the pool=100 user-profile workload we observed
	 * pending acquires while the pool still reported many idle connections, pointing at
	 * pool handoff/drain overhead rather than the SQL path. This bean keeps the same
	 * r2dbc-pool wrapper and Spring Data repository surface, while moving pool
	 * acquisition handoff onto a dedicated scheduler instead of whichever
	 * event-loop/acquire/release thread happens to win the drain loop.
	 * @param properties the Spring Boot R2DBC properties (URL, credentials, pool sizing)
	 * @param customizers ordered connection-factory option customizers from other beans
	 * @param acquisitionScheduler dedicated scheduler the pool acquisition handoff runs on
	 * @return the r2dbc-pool {@link ConnectionPool} wrapping the configured delegate factory
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
	 * event-loop workers instead of pinning it to one or two loops. Gated with
	 * {@code matchIfMissing=false}, so when the property is unset (e.g. the default
	 * profile) the bean is NOT registered and r2dbc-pool keeps its default
	 * {@code colocate=true} — toggled by property, no recompile needed.
	 * @return a customizer that installs a fan-out {@link LoopResources} to disable
	 * connection-to-event-loop colocation
	 */
	@Bean
	@ConditionalOnProperty(prefix = "app.r2dbc", name = "disable-colocation", havingValue = "true",
			matchIfMissing = false)
	public ConnectionFactoryOptionsBuilderCustomizer disableR2dbcLoopColocation() {
		int workerCount = Math.max(Runtime.getRuntime().availableProcessors(), 4);
		LoopResources fanOutLoops = LoopResources.create("r2dbc-loop", -1, // selectCount:
																			// -1 =
																			// reactor-netty
																			// default
				workerCount, // worker threads, one per CPU core
				true, // daemon: shut down with the JVM
				false // colocate: false = fan work across workers
		);
		return builder -> builder.option(R2DBC_LOOP_RESOURCES, fanOutLoops);
	}

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
