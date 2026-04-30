package ro.tweebyte.equivalence.support;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Truncates per-service Postgres databases between scenarios so tests don't
 * leak state into each other.
 *
 * Connects to each of the three exposed Postgres ports (54321 / 54322 / 54323)
 * on localhost — published by infrastructure.yml. Uses the well-known
 * postgres/postgres credentials from infrastructure.yml.
 *
 * Tables are introspected from the live schema and TRUNCATE ... CASCADE is
 * issued to clear all of them in one shot, except `flyway_schema_history` /
 * Hibernate's metadata tables (none of which currently exist, but defensive).
 */
public class DbReset {

    private record Db(String name, int port) {}

    private static final List<Db> DBS = List.of(
            new Db("user_service_db",        54321),
            new Db("tweet_service_db",       54322),
            new Db("interaction_service_db", 54323)
    );

    private DbReset() {}

    public static void clearAll() {
        for (Db db : DBS) {
            clear(db);
        }
        flushRedis();
    }

    /**
     * Flush every Redis db on the dev/CI Redis published at localhost:63790.
     * Without this the @Cacheable / Redis-list / Redis-set caches in
     * interaction-service (popular_users, popular_hashtags, follow_recommendations,
     * tweets:, users::, etc) leak across scenarios — the very first scenario
     * primes a key with empty data and every later scenario short-circuits the
     * compute branch. We open a socket and send a raw FLUSHALL RESP command so
     * no Redis client dep needs to be added.
     */
    private static void flushRedis() {
        try (Socket sock = new Socket("127.0.0.1", 63790)) {
            sock.setSoTimeout(2_000);
            OutputStream out = sock.getOutputStream();
            // RESP2: *1\r\n$8\r\nFLUSHALL\r\n
            out.write("*1\r\n$8\r\nFLUSHALL\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            // Drain at least the +OK\r\n reply so the server doesn't see a half-closed pipe.
            InputStream in = sock.getInputStream();
            byte[] buf = new byte[64];
            // best-effort read; ignore exceptions / EOF
            try { in.read(buf); } catch (Exception ignored) {}
        } catch (Exception e) {
            // Redis may be unavailable in non-FE contexts (unit tests); be loud but don't fail.
            System.err.println("[DbReset] WARN: Redis FLUSHALL on 127.0.0.1:63790 failed: " + e.getMessage());
        }
    }

    private static void clear(Db db) {
        String url = "jdbc:postgresql://127.0.0.1:" + db.port + "/" + db.name;
        try (Connection c = DriverManager.getConnection(url, "postgres", "postgres");
             Statement s = c.createStatement()) {
            // List user-schema tables, excluding any metadata.
            var rs = s.executeQuery(
                    "SELECT tablename FROM pg_tables " +
                    "WHERE schemaname='public' " +
                    "  AND tablename NOT LIKE 'flyway%' " +
                    "  AND tablename NOT LIKE 'pg_%'");
            StringBuilder qualified = new StringBuilder();
            while (rs.next()) {
                if (qualified.length() > 0) qualified.append(", ");
                qualified.append("\"").append(rs.getString(1)).append("\"");
            }
            rs.close();
            if (qualified.length() == 0) return;
            s.execute("TRUNCATE TABLE " + qualified + " RESTART IDENTITY CASCADE");
        } catch (SQLException e) {
            // Postgres might not be up yet on the very first scenario — be loud but don't kill the run.
            System.err.println("[DbReset] WARN: could not clear " + db.name + " on " + db.port + ": " + e.getMessage());
        }
    }
}
