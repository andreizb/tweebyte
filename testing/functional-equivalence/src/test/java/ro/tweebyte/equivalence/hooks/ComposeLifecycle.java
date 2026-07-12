/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.hooks;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;

import ro.tweebyte.equivalence.support.FeTls;
import ro.tweebyte.equivalence.support.StackProfile;

/**
 * Brings the Tweebyte stack up via `./run.sh runtime up {@code <stack>}
 * functional-equivalence` before the suite starts and tears it down after.
 *
 * `functional-equivalence` profile (defined in deployment/docker-compose/compose.sh +
 * deployment/docker-compose/functional-equivalence.yml) layers a JaCoCo agent into each
 * service JVM so per-service coverage from Cucumber scenarios can be aggregated. The
 * benchmark / prod paths are untouched.
 *
 * If the FE_REUSE_STACK system property is "true", neither up nor down is issued — useful
 * when iterating locally with the stack already running.
 */
public final class ComposeLifecycle {

	private static final boolean REUSE = Boolean.parseBoolean(System.getProperty("FE_REUSE_STACK", "false"));

	// Generous: under JaCoCo instrumentation each service can take 60-90s to be ready
	// even after the container reports "Started" — that's just bytecode rewriting on
	// every loaded class.
	private static final long BOOT_TIMEOUT_SECONDS = 420;

	private static final String JACOCO_VERSION = "0.8.12";

	private static final java.util.Map<String, Integer> JACOCO_PORTS = java.util.Map.of("gateway-service", 6301,
			"user-service", 6302, "tweet-service", 6303, "interaction-service", 6304);

	/**
	 * Probe ports the FE suite needs reachable. The prod / functional-equivalence stack
	 * serves TLS, so the probes are https: the gateway edge is client-auth=none and the
	 * backends are client-auth=need — {@link FeTls} installs the dev-CA trust + a client
	 * identity so both handshakes succeed. Reactive's Spring Cloud Gateway doesn't expose
	 * /actuator/health by default (404 there), so for the gateway we probe / and accept
	 * anything other than connection-refused as evidence Netty is up. The downstream
	 * services' /actuator/health (open, excluded from the basic-auth actuator chain)
	 * remains a strict 2xx check.
	 */
	private static final java.util.List<String> STRICT_2XX_PROBES = java.util.List.of(
			"https://localhost:9091/actuator/health", "https://localhost:9092/actuator/health",
			"https://localhost:9093/actuator/health");

	private static final java.util.List<String> ANY_RESPONSE_PROBES = java.util.List.of("https://localhost:8080/");

	private ComposeLifecycle() {
	}

	@BeforeAll
	public static void bringStackUp() throws IOException, InterruptedException {
		Path repoRoot = Path.of(System.getProperty("fe.repo.root", ".")).toAbsolutePath().normalize();
		StackProfile stack = StackProfile.current();
		ensureJacocoAgentExists(repoRoot);
		ensureJacocoCliExists(repoRoot);
		// The stack serves TLS on the functional-equivalence profile; trust the dev CA
		// and
		// present a client identity so the https readiness probes below handshake
		// cleanly.
		FeTls.install();
		Files.createDirectories(repoRoot.resolve("testing-results/functional-equivalence/jacoco").resolve(stack.composeName()));

		if (REUSE) {
			System.out.println("[ComposeLifecycle] FE_REUSE_STACK=true — assuming stack is already up.");
		}
		else {
			System.out.println("[ComposeLifecycle] Bringing up " + stack.composeName()
					+ " stack on functional-equivalence profile…");
			run(repoRoot, "./run.sh", "runtime", "up", stack.composeName(), "functional-equivalence");
		}

		waitForGateway();
	}

	@AfterAll
	public static void bringStackDown() throws IOException, InterruptedException {
		if (REUSE) {
			return;
		}
		Path repoRoot = Path.of(System.getProperty("fe.repo.root", ".")).toAbsolutePath().normalize();
		StackProfile stack = StackProfile.current();
		dumpJacocoCoverage(repoRoot, stack);
		System.out.println("[ComposeLifecycle] Tearing down " + stack.composeName() + " stack…");
		run(repoRoot, "./run.sh", "runtime", "down", stack.composeName(), "functional-equivalence");
	}

	private static void ensureJacocoAgentExists(Path repoRoot) {
		Path agent = repoRoot.resolve("testing/functional-equivalence/target/jacoco-agent.jar");
		if (!Files.exists(agent)) {
			throw new IllegalStateException("Missing JaCoCo agent jar at " + agent
					+ " — run `mvn -f testing/functional-equivalence/pom.xml process-test-resources` first "
					+ "(failsafe normally extracts it before the suite starts).");
		}
	}

	private static void ensureJacocoCliExists(Path repoRoot) {
		Path cli = jacocoCli(repoRoot);
		if (!Files.exists(cli)) {
			throw new IllegalStateException("Missing JaCoCo CLI jar at " + cli
					+ " — run `mvn -f testing/functional-equivalence/pom.xml process-test-resources` first "
					+ "(failsafe normally extracts it before the suite starts).");
		}
	}

	private static Path jacocoCli(Path repoRoot) {
		Path copied = repoRoot.resolve("testing/functional-equivalence/target/jacoco-cli.jar");
		if (Files.exists(copied)) {
			return copied;
		}
		return Path.of(System.getProperty("user.home"), ".m2", "repository", "org", "jacoco", "org.jacoco.cli",
				JACOCO_VERSION, "org.jacoco.cli-" + JACOCO_VERSION + "-nodeps.jar");
	}

	private static void dumpJacocoCoverage(Path repoRoot, StackProfile stack) throws IOException, InterruptedException {
		Path outDir = repoRoot.resolve("testing-results/functional-equivalence/jacoco").resolve(stack.composeName());
		Files.createDirectories(outDir);
		Path cli = jacocoCli(repoRoot);
		for (java.util.Map.Entry<String, Integer> entry : JACOCO_PORTS.entrySet()) {
			Path dest = outDir.resolve(entry.getKey() + ".exec");
			Files.deleteIfExists(dest);
			System.out.println("[ComposeLifecycle] Dumping JaCoCo coverage for " + entry.getKey() + "…");
			run(repoRoot, "java", "-jar", cli.toString(), "dump", "--address", "localhost", "--port",
					Integer.toString(entry.getValue()), "--destfile", dest.toString(), "--retry", "20", "--quiet");
		}
	}

	private static void run(Path cwd, String... cmd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile()).inheritIO();
		Process p = pb.start();
		int exit = p.waitFor();
		if (exit != 0) {
			throw new RuntimeException("Command " + String.join(" ", cmd) + " exited " + exit);
		}
	}

	private static void waitForGateway() throws InterruptedException {
		long deadline = System.nanoTime() + BOOT_TIMEOUT_SECONDS * 1_000_000_000L;
		int sweep = 0;
		int total = STRICT_2XX_PROBES.size() + ANY_RESPONSE_PROBES.size();
		while (System.nanoTime() < deadline) {
			sweep++;
			int healthy = 0;
			String firstUnhealthy = null;
			for (String url : STRICT_2XX_PROBES) {
				if (probe(url, true)) {
					healthy++;
				}
				else if (firstUnhealthy == null) {
					firstUnhealthy = url;
				}
			}
			for (String url : ANY_RESPONSE_PROBES) {
				if (probe(url, false)) {
					healthy++;
				}
				else if (firstUnhealthy == null) {
					firstUnhealthy = url;
				}
			}
			if (healthy == total) {
				System.out
					.println("[ComposeLifecycle] All " + healthy + " services healthy after " + sweep + " sweep(s).");
				return;
			}
			if (sweep % 10 == 0) {
				System.out.println("[ComposeLifecycle] Sweep " + sweep + ": " + healthy + "/" + total
						+ " healthy; still waiting on " + firstUnhealthy);
			}
			Thread.sleep(1_000);
		}
		throw new IllegalStateException("Stack never became healthy within " + BOOT_TIMEOUT_SECONDS + "s.");
	}

	private static boolean probe(String url, boolean require2xx) {
		try {
			HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
			conn.setConnectTimeout(2_000);
			conn.setReadTimeout(2_000);
			int code = conn.getResponseCode();
			conn.disconnect();
			// For the "any-response" probes, ANY HTTP code (including 4xx) means the
			// service is up — only IOException means the service is not reachable yet.
			return !require2xx || (code >= 200 && code < 300);
		}
		catch (IOException ex) {
			return false;
		}
	}

}
