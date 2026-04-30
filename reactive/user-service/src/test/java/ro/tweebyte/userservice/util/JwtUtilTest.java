package ro.tweebyte.userservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reactive analogue to async's JwtUtilTest. The reactive JwtUtil does not expose
 * a NimbusJwtDecoder (WebFlux security wires the public key directly), so the
 * second test exercises the keystore-bound rsaPrivateKey path while still
 * asserting on the BCryptPasswordEncoder bean.
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "keyAlias", "missing-alias");
        ReflectionTestUtils.setField(jwtUtil, "privateKeyPassphrase", "phrase");
    }

    @Test
    void testRsaPrivateKeyMissingAliasThrowsIllegalArgumentException() throws Exception {
        // Empty keystore → alias lookup returns null → JwtUtil rethrows as
        // IllegalArgumentException("Unable to load private key").
        KeyStore empty = KeyStore.getInstance(KeyStore.getDefaultType());
        empty.load(null, "password".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPrivateKey(empty));
    }

    @Test
    void testBCryptPasswordEncoder() {
        BCryptPasswordEncoder encoder = jwtUtil.bCryptPasswordEncoder();

        assertNotNull(encoder);
    }
}
