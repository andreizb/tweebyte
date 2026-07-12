/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Truncates per-service Postgres databases between scenarios so tests don't leak state
 * into each other.
 *
 * Connects to each of the three exposed Postgres ports (54321 / 54322 / 54323) on
 * localhost — published by infrastructure.yml. Uses the well-known postgres/postgres
 * credentials from infrastructure.yml.
 *
 * Tables are introspected from the live schema and TRUNCATE ... CASCADE is issued to
 * clear all of them in one shot, except `flyway_schema_history` / Hibernate's metadata
 * tables (none of which currently exist, but defensive).
 */
public final class DbReset {

	private static final List<Db> DBS = List.of(new Db("user_service_db", 54321), new Db("tweet_service_db", 54322),
			new Db("interaction_service_db", 54323));

	private static final String KEYCLOAK_BASE = System.getProperty("fe.keycloak.base.url", "http://localhost:8090");

	private static final String KEYCLOAK_REALM = "tweebyte";

	/**
	 * Loopback host for the locally-published infra. Resolved from
	 * {@link java.net.InetAddress#getLoopbackAddress()} rather than a literal so the
	 * databases, Redis and Keycloak are all reached over the same interface the compose
	 * stack publishes ports on.
	 */
	private static final String LOOPBACK_HOST = InetAddress.getLoopbackAddress().getHostAddress();

	private static final int REDIS_PORT = Integer.getInteger("fe.redis.port", 63790);

	// Dev/CI Postgres credentials (from infrastructure.yml); overridable for other envs.
	private static final String PG_USER = System.getProperty("fe.pg.user", "postgres");

	private static final String PG_PASSWORD = System.getProperty("fe.pg.password", "postgres");

	// Constant, parameter-free reset run entirely server-side (see clear()): introspect +
	// truncate happen inside PL/pgSQL, so no identifier is ever spliced into client SQL.
	// The literal id below is the all-zeros default-avatar sentinel seeded by Flyway V1,
	// which must survive every reset.
	private static final String RESET_SCHEMA_SQL = """
			DO $$
			DECLARE
				target text;
				has_media boolean := false;
			BEGIN
				FOR target IN
					SELECT tablename FROM pg_tables
					WHERE schemaname = 'public'
						AND tablename NOT LIKE 'flyway%'
						AND tablename NOT LIKE 'pg_%'
				LOOP
					IF target = 'media_assets' THEN
						has_media := true;
					ELSE
						EXECUTE format('TRUNCATE TABLE %I RESTART IDENTITY CASCADE', target);
					END IF;
				END LOOP;
				IF has_media THEN
					DELETE FROM media_assets
					WHERE id <> '00000000-0000-0000-0000-000000000000';
				END IF;
			END $$;
			""";

	private DbReset() {
	}

	public static void clearAll() {
		for (Db db : DBS) {
			clear(db);
		}
		flushRedis();
		flushKeycloak();
	}

	/**
	 * Delete every provisioned user from the Keycloak {@code tweebyte} realm (except the
	 * tweebyte-app service account) between scenarios. Since W2, registration provisions
	 * a Keycloak credential keyed by email; truncating only the local Postgres row leaves
	 * the Keycloak user behind, so the NEXT scenario that registers the same email gets a
	 * 409 from the Admin API → mapped to a 502 by the user-service. Clearing the realm
	 * here keeps the IdP in lock-step with the truncated databases. Dev master-admin
	 * creds (admin/admin) come from infrastructure.yml; overridable via the fe.keycloak.*
	 * system properties.
	 */
	private static void flushKeycloak() {
		try {
			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
			String token = keycloakAdminToken(http);
			ObjectMapper mapper = new ObjectMapper();
			HttpResponse<String> list = http.send(HttpRequest
				.newBuilder(URI.create(KEYCLOAK_BASE + "/admin/realms/" + KEYCLOAK_REALM + "/users?max=10000"))
				.header("Authorization", "Bearer " + token)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());
			if (list.statusCode() != 200) {
				System.err.println("[DbReset] WARN: Keycloak user list returned " + list.statusCode());
				return;
			}
			for (JsonNode user : mapper.readTree(list.body())) {
				String username = user.path("username").asText("");
				// Keep the client's service account — it backs the Admin-API grant
				// itself.
				if (username.startsWith("service-account-")) {
					continue;
				}
				String id = user.path("id").asText();
				http.send(HttpRequest
					.newBuilder(URI.create(KEYCLOAK_BASE + "/admin/realms/" + KEYCLOAK_REALM + "/users/" + id))
					.header("Authorization", "Bearer " + token)
					.DELETE()
					.build(), HttpResponse.BodyHandlers.discarding());
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			System.err.println("[DbReset] WARN: Keycloak realm flush interrupted: " + ex.getMessage());
		}
		catch (IOException | RuntimeException ex) {
			// Keycloak may be unavailable in non-FE contexts; be loud but don't fail the
			// run.
			System.err.println("[DbReset] WARN: Keycloak realm flush failed: " + ex.getMessage());
		}
	}

	private static String keycloakAdminToken(HttpClient http) throws IOException, InterruptedException {
		String admin = System.getProperty("fe.keycloak.admin.user", "admin");
		String password = System.getProperty("fe.keycloak.admin.password", "admin");
		String form = "grant_type=password&client_id=admin-cli" + "&username="
				+ URLEncoder.encode(admin, StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(password, StandardCharsets.UTF_8);
		HttpResponse<String> resp = http
			.send(HttpRequest.newBuilder(URI.create(KEYCLOAK_BASE + "/realms/master/protocol/openid-connect/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build(), HttpResponse.BodyHandlers.ofString());
		return new ObjectMapper().readTree(resp.body()).path("access_token").asText();
	}

	/**
	 * Flush every Redis db on the dev/CI Redis published at localhost:63790. Without this
	 * the @Cacheable / Redis-list / Redis-set caches in interaction-service
	 * (popular_users, popular_hashtags, follow_recommendations, tweets:, users::, etc)
	 * leak across scenarios — the very first scenario primes a key with empty data and
	 * every later scenario short-circuits the compute branch. We open a socket and send a
	 * raw FLUSHALL RESP command so no Redis client dep needs to be added.
	 */
	private static void flushRedis() {
		try (Socket sock = new Socket(LOOPBACK_HOST, REDIS_PORT)) {
			sock.setSoTimeout(2_000);
			OutputStream out = sock.getOutputStream();
			// RESP2: *1\r\n$8\r\nFLUSHALL\r\n
			out.write("*1\r\n$8\r\nFLUSHALL\r\n".getBytes(StandardCharsets.US_ASCII));
			out.flush();
			// Drain the +OK\r\n reply so the server doesn't see a half-closed pipe.
			drainReply(sock.getInputStream());
		}
		catch (IOException | RuntimeException ex) {
			// Redis may be unavailable in non-FE contexts (unit tests); be loud but don't
			// fail.
			System.err.println("[DbReset] WARN: Redis FLUSHALL on " + LOOPBACK_HOST + ":" + REDIS_PORT + " failed: "
					+ ex.getMessage());
		}
	}

	/**
	 * Best-effort read of the short {@code +OK} RESP reply, consuming the bytes so the
	 * remote end sees a clean close. Stops at the first read that returns no bytes (EOF,
	 * an empty read, or a socket-timeout) — the reply is only a handful of bytes.
	 */
	private static void drainReply(InputStream in) {
		byte[] buf = new byte[64];
		try {
			int read = in.read(buf);
			while (read == buf.length) {
				read = in.read(buf);
			}
		}
		catch (IOException ignored) {
			// EOF / read-timeout on a best-effort drain is fine — nothing depends on it.
		}
	}

	private static void clear(Db db) {
		String url = "jdbc:postgresql://" + LOOPBACK_HOST + ":" + db.port + "/" + db.name;
		try (Connection c = DriverManager.getConnection(url, PG_USER, PG_PASSWORD);
				Statement s = c.createStatement()) {
			// One constant server-side DO block does the introspection and the dynamic
			// TRUNCATE inside PL/pgSQL, so nothing is spliced into SQL on the client. It
			// truncates every public user table (RESTART IDENTITY CASCADE) except Flyway /
			// pg_ metadata and media_assets, then drops only the test-uploaded media rows.
			// media_assets carries the permanent all-zeros default-avatar sentinel (seeded
			// by Flyway V1) that every registration's profile_picture_id FK depends on:
			// truncating it would orphan that FK and break every later registration, so it
			// is cleared selectively (keep the sentinel) and only after the user tables are
			// gone, when no profile_picture_id FK still points into it.
			s.execute(RESET_SCHEMA_SQL);
		}
		catch (SQLException ex) {
			// Postgres might not be up yet on the very first scenario — be loud but don't
			// kill the run.
			System.err.println("[DbReset] WARN: could not clear " + db.name + " on " + db.port + ": " + ex.getMessage());
		}
	}

	private record Db(String name, int port) {
	}

}
