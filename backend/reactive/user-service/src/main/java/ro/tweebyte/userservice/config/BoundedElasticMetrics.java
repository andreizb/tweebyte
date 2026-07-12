/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class BoundedElasticMetrics {

	private static final String BOUNDED_ELASTIC_SCHEDULER = "BoundedElasticScheduler";

	// Field names Reactor uses for the scheduler it wraps; following these lets us
	// unwrap decorators down to the concrete BoundedElasticScheduler.
	private static final Set<String> DELEGATE_FIELD_NAMES = Set.of("actual", "delegate", "source", "scheduler", "sc",
			"s");

	private final PrometheusMeterRegistry registry;

	private final AtomicReference<Handles> handlesRef = new AtomicReference<>();

	public BoundedElasticMetrics(PrometheusMeterRegistry registry) {
		this.registry = registry;
	}

	@PostConstruct
	public void init() {
		Gauge.builder("reactor_bounded_elastic_queue_utilization", this, m -> {
			Values v = m.readValues();
			if (v == null) {
				return Double.NaN;
			}
			if (v.maxTasksTotal <= 0) {
				return 0.0;
			}
			double used = (double) v.maxTasksTotal - v.remainingCapacity;
			return Math.clamp(used / v.maxTasksTotal, 0.0, 1.0);
		}).description("How full the boundedElastic queues are (0..1)").register(this.registry);

		Gauge.builder("reactor_bounded_elastic_queue_size", this, m -> {
			Values v = m.readValues();
			if (v == null) {
				return Double.NaN;
			}
			long used = v.maxTasksTotal - v.remainingCapacity;
			return (used < 0) ? 0 : (double) used;
		}).description("Total waiting tasks across all boundedElastic workers").register(this.registry);
	}

	private Values readValues() {
		try {
			Handles h = this.handlesRef.updateAndGet(old -> (old != null) ? old : discover());
			if (h == null) {
				return null;
			}

			Object real = h.realSchedulerGetter.get();
			int remaining = (int) h.estimateRemainingTaskCapacity.invoke(real);
			if (remaining < 0) {
				return null;
			}
			int maxThreads = (int) h.maxThreadsField.get(real);
			int perThreadQ = (int) h.maxTaskQueuedPerThreadField.get(real);
			long maxTotal = (long) maxThreads * (long) perThreadQ;

			return new Values(remaining, maxThreads, perThreadQ, maxTotal);
		}
		catch (ReflectiveOperationException | RuntimeException ex) {
			log.debug("boundedElastic reflection failed: {}", ex.toString());
			return null;
		}
	}

	private Handles discover() {
		try {
			Scheduler s = Schedulers.boundedElastic();
			Object real = unwrap(s, 6);
			if (real == null) {
				log.warn("Couldn’t unwrap boundedElastic to BoundedElasticScheduler");
				return null;
			}

			Class<?> cls = real.getClass();
			Method estimate = cls.getDeclaredMethod("estimateRemainingTaskCapacity");
			estimate.setAccessible(true);

			Field fMaxThreads = cls.getDeclaredField("maxThreads");
			fMaxThreads.setAccessible(true);

			Field fMaxQueuedPerThread = cls.getDeclaredField("maxTaskQueuedPerThread");
			fMaxQueuedPerThread.setAccessible(true);

			return new Handles(() -> real, estimate, fMaxThreads, fMaxQueuedPerThread);
		}
		catch (ReflectiveOperationException | RuntimeException ex) {
			log.warn("Failed to discover boundedElastic internals: {}", ex.toString());
			return null;
		}
	}

	private static boolean isBoundedElasticScheduler(Object obj) {
		Class<?> cls = obj.getClass();
		return cls.getSimpleName().contains(BOUNDED_ELASTIC_SCHEDULER)
				|| cls.getName().endsWith(BOUNDED_ELASTIC_SCHEDULER);
	}

	private static Object unwrap(Object obj, int depth) {
		if (obj == null || depth <= 0) {
			return null;
		}
		if (isBoundedElasticScheduler(obj)) {
			return obj;
		}
		for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				Object found = unwrapField(obj, f, depth);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static Object unwrapField(Object owner, Field f, int depth) {
		try {
			f.setAccessible(true);
			Object v = f.get(owner);
			if (v == null) {
				return null;
			}
			if (isBoundedElasticScheduler(v)) {
				return v;
			}
			return DELEGATE_FIELD_NAMES.contains(f.getName()) ? unwrap(v, depth - 1) : null;
		}
		catch (IllegalAccessException ex) {
			// field not readable under this module layer; skip it and try the next one
			return null;
		}
	}

	private record Values(int remainingCapacity, int maxThreads, int perThreadQ, long maxTasksTotal) {
	}

	private record Handles(RealGetter realSchedulerGetter, Method estimateRemainingTaskCapacity, Field maxThreadsField,
			Field maxTaskQueuedPerThreadField) {
	}

	@FunctionalInterface
	private interface RealGetter {

		Object get();

	}

}
