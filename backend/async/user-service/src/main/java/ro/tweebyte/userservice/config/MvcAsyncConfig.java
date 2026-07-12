/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code streamExecutor} — long-held streaming pool, isolated from {@code ioExecutor} so
 * a slow file download can't starve fast DB queries. Spring MVC drives
 * {@code StreamingResponseBody} returns (file-download, GET /media/) on this executor via
 * {@link #configureAsyncSupport}. Sized wide (1000) because each download parks its
 * thread for the throttled transfer.
 *
 * @author Andrei Zbarcea
 */
@Configuration
public class MvcAsyncConfig implements WebMvcConfigurer {

	@Value("${app.concurrency.stream.pool-size:1000}")
	private int poolSize;

	@Value("${app.concurrency.stream.queue-capacity:100000}")
	private int queueCapacity;

	@Bean(name = "streamExecutor")
	public ThreadPoolTaskExecutor streamExecutor() {
		ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
		ex.setCorePoolSize(this.poolSize);
		ex.setMaxPoolSize(this.poolSize);
		ex.setQueueCapacity(this.queueCapacity);
		ex.setThreadNamePrefix("stream-");
		ex.setAllowCoreThreadTimeOut(true);
		ex.setKeepAliveSeconds(60);
		ex.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
		ex.initialize();
		return ex;
	}

	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer c) {
		c.setTaskExecutor(streamExecutor());
		c.setDefaultTimeout(0);
	}

}
