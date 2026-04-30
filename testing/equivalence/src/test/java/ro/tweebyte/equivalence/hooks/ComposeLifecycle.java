package ro.tweebyte.equivalence.hooks;

import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import ro.tweebyte.equivalence.support.StackProfile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Brings the Tweebyte stack up via `./run.sh runtime up <stack> fe-test`
 * before the suite starts and tears it down after.
 *
 * `fe-test` profile (defined in infrastructure/run.sh + infrastructure/compose/fe-test.yml)
 * layers a JaCoCo agent into each service JVM so per-service coverage from
 * Cucumber scenarios can be aggregated. The benchmark / prod paths are
 * untouched.
 *
 * If the FE_REUSE_STACK system property is "true", neither up nor down is
 * issued — useful when iterating locally with the stack already running.
 */
public class ComposeLifecycle {

    private static final boolean REUSE = Boolean.parseBoolean(System.getProperty("FE_REUSE_STACK", "false"));
    // Generous: under JaCoCo instrumentation each service can take 60-90s to be ready
    // even after the container reports "Started" — that's just bytecode rewriting on
    // every loaded class.
    private static final long BOOT_TIMEOUT_SECONDS = 420;

    /**
     * Probe ports the FE suite needs reachable. Reactive's Spring Cloud
     * Gateway doesn't expose /actuator/health by default (404 there), so for
     * the gateway we probe / and accept anything other than connection-refused
     * as evidence Netty is up. The downstream services' /actuator/health
     * remains a strict 2xx check.
     */
    private static final java.util.List<String> STRICT_2XX_PROBES = java.util.List.of(
            "http://localhost:9091/actuator/health",
            "http://localhost:9092/actuator/health",
            "http://localhost:9093/actuator/health"
    );

    private static final java.util.List<String> ANY_RESPONSE_PROBES = java.util.List.of(
            "http://localhost:8080/"
    );

    @BeforeAll
    public static void bringStackUp() throws IOException, InterruptedException {
        Path repoRoot = Path.of(System.getProperty("fe.repo.root", ".")).toAbsolutePath().normalize();
        StackProfile stack = StackProfile.current();
        ensureJacocoAgentExists(repoRoot);
        Files.createDirectories(repoRoot.resolve("testing-results/equivalence/jacoco"));

        if (REUSE) {
            System.out.println("[ComposeLifecycle] FE_REUSE_STACK=true — assuming stack is already up.");
        } else {
            System.out.println("[ComposeLifecycle] Bringing up " + stack.composeName() + " stack on fe-test profile…");
            run(repoRoot, "./run.sh", "runtime", "up", stack.composeName(), "fe-test");
        }

        waitForGateway();
    }

    @AfterAll
    public static void bringStackDown() throws IOException, InterruptedException {
        if (REUSE) return;
        Path repoRoot = Path.of(System.getProperty("fe.repo.root", ".")).toAbsolutePath().normalize();
        StackProfile stack = StackProfile.current();
        System.out.println("[ComposeLifecycle] Tearing down " + stack.composeName() + " stack…");
        run(repoRoot, "./run.sh", "runtime", "down", stack.composeName(), "fe-test");
    }

    private static void ensureJacocoAgentExists(Path repoRoot) {
        Path agent = repoRoot.resolve("testing/equivalence/target/jacoco-agent.jar");
        if (!Files.exists(agent)) {
            throw new IllegalStateException(
                    "Missing JaCoCo agent jar at " + agent +
                    " — run `mvn -f testing/equivalence/pom.xml process-test-resources` first " +
                    "(failsafe normally extracts it before the suite starts).");
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
                if (probe(url, true)) healthy++;
                else if (firstUnhealthy == null) firstUnhealthy = url;
            }
            for (String url : ANY_RESPONSE_PROBES) {
                if (probe(url, false)) healthy++;
                else if (firstUnhealthy == null) firstUnhealthy = url;
            }
            if (healthy == total) {
                System.out.println("[ComposeLifecycle] All " + healthy + " services healthy after " + sweep + " sweep(s).");
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
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(2_000);
            c.setReadTimeout(2_000);
            int code = c.getResponseCode();
            c.disconnect();
            // For the "any-response" probes, ANY HTTP code (including 4xx) means the
            // service is up — only IOException means the service is not reachable yet.
            return require2xx ? (code >= 200 && code < 300) : true;
        } catch (IOException e) {
            return false;
        }
    }
}
