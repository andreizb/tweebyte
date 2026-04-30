package ro.tweebyte.equivalence.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-scenario shared state. Cucumber-Spring would normally inject a singleton
 * here via @ScenarioScope, but to avoid the spring-boot-test ceremony we keep
 * scenario state in a ThreadLocal and clear it in @Before / @After hooks.
 *
 * Holds: registered users (by handle), their JWTs, their UUIDs, and the latest
 * REST response so subsequent Then-steps can assert against it.
 */
public class ScenarioContext {

    private static final ThreadLocal<ScenarioContext> CTX = ThreadLocal.withInitial(ScenarioContext::new);

    public static ScenarioContext current() {
        return CTX.get();
    }

    public static void reset() {
        CTX.remove();
    }

    public final Map<String, String> jwtByHandle = new HashMap<>();
    public final Map<String, UUID>   userIdByHandle = new HashMap<>();
    public final Map<String, String> emailByHandle = new HashMap<>();
    public final Map<String, String> passwordByHandle = new HashMap<>();

    /** Tweet UUIDs tracked by content-string (used as a stable key in scenarios). */
    public final Map<String, UUID> lastTweetIdByContent = new HashMap<>();
    /** Tweet UUIDs tracked by author handle (most recent post). */
    public final Map<String, UUID> lastTweetIdByHandle = new HashMap<>();

    public int lastStatus;
    public String lastBody;
    public JsonNode lastJson;
    public String lastContentType;
    public String currentActor;
}
