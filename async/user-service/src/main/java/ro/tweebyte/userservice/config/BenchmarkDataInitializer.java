package ro.tweebyte.userservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds a deterministic benchmark user on startup via native SQL so the fixed
 * UUID survives (JPA's {@code @GeneratedValue} on UserEntity otherwise
 * substitutes a random UUID at persist time). Idempotent: no-op if a row with
 * the benchmark UUID already exists.
 */
@Component
@Profile("benchmark")
@RequiredArgsConstructor
@Slf4j
public class BenchmarkDataInitializer {

    public static final UUID BENCHMARK_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, BENCHMARK_USER_ID);
        if (existing != null && existing > 0) {
            log.info("Benchmark user {} already present; skipping seed.", BENCHMARK_USER_ID);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO users (id, user_name, email, biography, password, is_private, birth_date, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                BENCHMARK_USER_ID,
                "benchmark_user",
                "benchmark@tweebyte.local",
                "Deterministic user for AI W2 tool-use benchmarks.",
                "$2a$10$dummyhashnotusedinbenchmarks................",
                false,
                java.sql.Date.valueOf(LocalDate.of(1990, 1, 1)),
                Timestamp.valueOf(LocalDateTime.now()));
        log.info("Seeded benchmark user: {}", BENCHMARK_USER_ID);
    }
}
