package ro.tweebyte.userservice.config;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reactive counterpart to the async BenchmarkDataInitializer.
 * Uses DatabaseClient for a native INSERT so the fixed UUID is respected.
 * Blocks only on the initial seed (application-ready event, not a request path).
 */
@Component
@Profile("benchmark")
@RequiredArgsConstructor
@Slf4j
public class BenchmarkDataInitializer {

    public static final UUID BENCHMARK_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ConnectionFactory connectionFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        DatabaseClient client = DatabaseClient.create(connectionFactory);
        Long existing = client.sql("SELECT COUNT(*) AS c FROM users WHERE id = :id")
                .bind("id", BENCHMARK_USER_ID)
                .map(row -> row.get("c", Long.class))
                .one()
                .blockOptional()
                .orElse(0L);
        if (existing != null && existing > 0) {
            log.info("Benchmark user {} already present; skipping seed.", BENCHMARK_USER_ID);
            return;
        }
        client.sql("INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at) " +
                        "VALUES (:id, :user_name, :email, :biography, :password, :is_private, :birth_date, :created_at) " +
                        "ON CONFLICT DO NOTHING")
                .bind("id", BENCHMARK_USER_ID)
                .bind("user_name", "benchmark_user")
                .bind("email", "benchmark@tweebyte.local")
                .bind("biography", "Deterministic user for AI W2 tool-use benchmarks.")
                .bind("password", "$2a$10$dummyhashnotusedinbenchmarks................")
                .bind("is_private", false)
                .bind("birth_date", LocalDate.of(1990, 1, 1))
                .bind("created_at", LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .switchIfEmpty(Mono.just(0L))
                .block();
        log.info("Seeded benchmark user: {}", BENCHMARK_USER_ID);
    }
}
