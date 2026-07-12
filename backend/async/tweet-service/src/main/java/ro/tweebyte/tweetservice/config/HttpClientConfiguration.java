/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.time.Duration;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Outbound HTTP connection pooling for the blocking {@code RestClient} fan-out.
 *
 * <p>
 * Every downstream client ({@code UserClient}, {@code InteractionClient}) injects the
 * autoconfigured {@code RestClient.Builder}, so one {@link RestClientCustomizer} binds the
 * pooled Apache HttpClient 5 request factory to all of them at once — the blocking-stack
 * mirror of the reactive {@code WebClientCustomizer} + {@code ConnectionProvider}.
 *
 * <p>
 * Sized symmetrically with the reactive Reactor Netty {@code ConnectionProvider}:
 * {@link #maxConnectionsPerRoute} matches reactive's per-host {@code maxConnections},
 * and {@link #maxConnectionsTotal} covers both downstream routes (user-service +
 * interaction-service) at the per-route ceiling. The {@link #acquireTimeoutSeconds}
 * mirrors reactive's {@code pendingAcquireTimeout}: once a route's connections are all
 * leased, the {@code httpClientExecutor} thread parks for that long waiting for one to free.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class HttpClientConfiguration {

	private static final String SSL_BUNDLE = "tweebyte";

	// Downstream HTTP pool — sizes + acquire timeout declared in application.properties
	// (app.http.downstream.*) so the measured fan-out config is receipt-visible and symmetric
	// with reactive's Reactor Netty pool. The benchmark overlay raises the acquire timeout to
	// effectively-infinite (wait, don't fail).
	@Value("${app.http.downstream.max-connections:1000}")
	private int maxConnectionsPerRoute;

	@Value("${app.http.downstream.max-connections-total:2000}")
	private int maxConnectionsTotal;

	@Value("${app.http.downstream.acquire-timeout-seconds:45}")
	private long acquireTimeoutSeconds;

	// Response (socket-read) timeout — a safety net symmetric with reactive's
	// HttpClient.responseTimeout. Without it, a downstream that stops responding holds its
	// pooled connection until the server's async timeout 500s the outer request, but the
	// inner blocking read keeps the connection → pool depletes → collapse under deep fan-out.
	// Sized under the server async timeout. 28s is far above any healthy-cell latency, so it
	// never fires in a clean run; it only fails a pathological slow path fast.
	@Value("${app.http.downstream.response-timeout-seconds:28}")
	private long responseTimeoutSeconds;

	@Bean(destroyMethod = "close")
	public CloseableHttpClient pooledHttpClient(@Value("${app.mtls.enabled:false}") boolean mtlsEnabled,
			SslBundles sslBundles) {
		PoolingHttpClientConnectionManagerBuilder connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
			.setMaxConnTotal(this.maxConnectionsTotal)
			.setMaxConnPerRoute(this.maxConnectionsPerRoute);
		if (mtlsEnabled) {
			// East-west mTLS (prod / functional-equivalence): present this service's client
			// cert and trust the dev CA on every outbound RestClient call. Off in benchmark
			// (app.mtls.enabled=false), so the pool stays plaintext and the measured fan-out
			// pays no TLS cost.
			SSLContext sslContext = sslBundles.getBundle(SSL_BUNDLE).createSslContext();
			connectionManager.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
		}
		// Response timeout (time awaiting response data) on the client's default RequestConfig
		// — HttpClient 5's read-timeout equivalent. The request factory's per-request config
		// merge preserves it while overriding only connect/connection-request timeouts.
		RequestConfig requestConfig = RequestConfig.custom()
			.setResponseTimeout(Timeout.ofSeconds(this.responseTimeoutSeconds))
			.build();
		return HttpClients.custom()
			.setConnectionManager(connectionManager.build())
			.setDefaultRequestConfig(requestConfig)
			.build();
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	public RestClientCustomizer pooledRestClientCustomizer(CloseableHttpClient pooledHttpClient) {
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(
				pooledHttpClient);
		requestFactory.setConnectionRequestTimeout(Duration.ofSeconds(this.acquireTimeoutSeconds));
		return builder -> builder.requestFactory(requestFactory);
	}

}
