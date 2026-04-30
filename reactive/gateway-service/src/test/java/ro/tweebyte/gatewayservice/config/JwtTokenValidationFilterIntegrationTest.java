package ro.tweebyte.gatewayservice.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * End-to-end JWT validation behaviour through {@link JwtTokenValidationFilter}.
 * Covers the five required JWT invariants: valid/invalid/missing/expired/malformed,
 * plus the {@code /login} bypass path. Routes target unreachable localhost ports — for the
 * "valid token" path we only assert the response is NOT 401, which means the JWT layer cleared
 * and the request progressed to the routing/Netty layer (where it then fails with 5xx).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "20000")
// Override the routes wholesale so the downstream port is unreachable - we only assert
// whether the JWT filter accepted (not-401) or rejected (401) the request.
@TestPropertySource(locations = "classpath:application-test-jwt.properties")
class JwtTokenValidationFilterIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static RSAPrivateKey privateKey;
    private static RSAPublicKey publicKey;

    @BeforeAll
    static void loadKeys() throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("keystore.jks")) {
            ks.load(in, "password".toCharArray());
        }
        privateKey = (RSAPrivateKey) ks.getKey("tweebyte", "password".toCharArray());
        publicKey = (RSAPublicKey) ks.getCertificate("tweebyte").getPublicKey();
    }

    private String signValidToken() {
        Algorithm alg = Algorithm.RSA256(publicKey, privateKey);
        return JWT.create()
                .withSubject("integration-test-user")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(alg);
    }

    private String signExpiredToken() {
        Algorithm alg = Algorithm.RSA256(publicKey, privateKey);
        return JWT.create()
                .withSubject("expired-test-user")
                .withIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .withExpiresAt(new Date(System.currentTimeMillis() - 60_000))
                .sign(alg);
    }

    @Test
    void validTokenPassesJwtFilter() {
        String token = signValidToken();
        HttpStatusCode status = webTestClient.get()
                .uri("/user-service/anything")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .returnResult(Void.class).getStatus();
        assertNotEquals(HttpStatus.UNAUTHORIZED, status,
                "Valid token should not be rejected by JWT filter, got " + status);
    }

    @Test
    void invalidSignatureRejected() {
        // Token signed with wrong (HMAC) algorithm → RSA verifier rejects.
        String bogus = JWT.create()
                .withSubject("attacker")
                .sign(Algorithm.HMAC256("not-the-real-key"));
        webTestClient.get()
                .uri("/user-service/anything")
                .header("Authorization", "Bearer " + bogus)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void missingAuthorizationHeaderRejected() {
        webTestClient.get()
                .uri("/user-service/anything")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredTokenRejected() {
        String expired = signExpiredToken();
        webTestClient.get()
                .uri("/user-service/anything")
                .header("Authorization", "Bearer " + expired)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void malformedTokenRejected() {
        webTestClient.get()
                .uri("/user-service/anything")
                .header("Authorization", "Bearer asdfg")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginPathBypassesJwtFilter() {
        // No Authorization header, but /login should not be challenged by the JWT filter.
        HttpStatusCode status = webTestClient.get()
                .uri("/user-service/login")
                .exchange()
                .returnResult(Void.class).getStatus();
        assertNotEquals(HttpStatus.UNAUTHORIZED, status,
                "/login must bypass JWT validation, got " + status);
    }

}
