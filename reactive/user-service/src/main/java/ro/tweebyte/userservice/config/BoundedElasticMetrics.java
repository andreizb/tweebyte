package ro.tweebyte.userservice.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class BoundedElasticMetrics {

    private final PrometheusMeterRegistry registry;
    public BoundedElasticMetrics(PrometheusMeterRegistry registry) { this.registry = registry; }

    private final AtomicReference<Handles> handlesRef = new AtomicReference<>();

    @PostConstruct
    public void init() {
        Gauge.builder("reactor_bounded_elastic_queue_utilization", this, m -> {
                Values v = m.readValues();
                if (v == null) return Double.NaN;
                if (v.maxTasksTotal <= 0) return 0.0;
                double used = v.maxTasksTotal - v.remainingCapacity;
                double ratio = used / (double) v.maxTasksTotal;

                if (ratio < 0) ratio = 0;
                if (ratio > 1) ratio = 1;
                return ratio;
            })
            .description("How full the boundedElastic queues are (0..1)")
            .register(registry);

        Gauge.builder("reactor_bounded_elastic_queue_size", this, m -> {
                Values v = m.readValues();
                if (v == null) return Double.NaN;
                long used = v.maxTasksTotal - v.remainingCapacity;
                return used < 0 ? 0 : (double) used;
            })
            .description("Total waiting tasks across all boundedElastic workers")
            .register(registry);
    }

    private Values readValues() {
        try {
            Handles h = handlesRef.updateAndGet(old -> old != null ? old : discover());
            if (h == null) return null;

            Object real = h.realSchedulerGetter.get();
            int remaining = (int) h.estimateRemainingTaskCapacity.invoke(real);
            if (remaining < 0) {
                return null;
            }
            int maxThreads  = (int) h.maxThreadsField.get(real);
            int perThreadQ  = (int) h.maxTaskQueuedPerThreadField.get(real);
            long maxTotal   = (long) maxThreads * (long) perThreadQ;

            return new Values(remaining, maxThreads, perThreadQ, maxTotal);
        } catch (Throwable t) {
            log.debug("boundedElastic reflection failed: {}", t.toString());
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

            return new Handles(
                () -> real, estimate, fMaxThreads, fMaxQueuedPerThread
            );
        } catch (Throwable t) {
            log.warn("Failed to discover boundedElastic internals: {}", t.toString());
            return null;
        }
    }

    private static Object unwrap(Object obj, int depth) {
        if (obj == null || depth <= 0) return null;
        if (obj.getClass().getSimpleName().contains("BoundedElasticScheduler") ||
            obj.getClass().getName().endsWith("BoundedElasticScheduler")) {
            return obj;
        }
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;
                    String sn = v.getClass().getSimpleName();
                    String qn = v.getClass().getName();
                    if (sn.contains("BoundedElasticScheduler") || qn.endsWith("BoundedElasticScheduler")) {
                        return v;
                    }
                    if (List.of("actual","delegate","source","scheduler","sc","s").contains(f.getName())) {
                        Object deeper = unwrap(v, depth - 1);
                        if (deeper != null) return deeper;
                    }
                } catch (IllegalAccessException ignored) {}
            }
        }
        return null;
    }

    private record Values(int remainingCapacity, int maxThreads, int perThreadQ, long maxTasksTotal) {}
    private record Handles(RealGetter realSchedulerGetter, Method estimateRemainingTaskCapacity,
                           Field maxThreadsField, Field maxTaskQueuedPerThreadField) {}
    @FunctionalInterface private interface RealGetter { Object get(); }
}
