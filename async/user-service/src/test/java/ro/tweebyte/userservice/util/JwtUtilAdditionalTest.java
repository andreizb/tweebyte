package ro.tweebyte.userservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilAdditionalTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setup() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "keyStorePath", "no-such-keystore.jks");
        ReflectionTestUtils.setField(jwtUtil, "keyStorePassword", "password");
        ReflectionTestUtils.setField(jwtUtil, "keyAlias", "tweebyte");
        ReflectionTestUtils.setField(jwtUtil, "privateKeyPassphrase", "password");
    }

    @Test
    void keyStoreThrowsIllegalArgumentWhenPasswordNull() {
        ReflectionTestUtils.setField(jwtUtil, "keyStorePassword", null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.keyStore());
        assertEquals("Unable to load keystore", ex.getMessage());
    }

    @Test
    void keyStoreLoadsRealKeystoreFromTestClasspath() {
        ReflectionTestUtils.setField(jwtUtil, "keyStorePath", "keystore.jks");
        KeyStore ks = jwtUtil.keyStore();
        assertNotNull(ks);
    }

    @Test
    void rsaPrivateKeyThrowsWhenKeyStoreThrows() throws Exception {
        KeyStore ks = mock(KeyStore.class);
        when(ks.getKey(org.mockito.ArgumentMatchers.eq("tweebyte"), org.mockito.ArgumentMatchers.any()))
            .thenThrow(new RuntimeException("bad"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPrivateKey(ks));
        assertEquals("Unable to load private key", ex.getMessage());
    }

    @Test
    void rsaPrivateKeyThrowsWhenKeyIsNotRsa() throws Exception {
        KeyStore ks = mock(KeyStore.class);
        // Returns a non-RSA key (mock as plain Key) -> falls through to throw
        when(ks.getKey(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(java.security.Key.class));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPrivateKey(ks));
        assertEquals("Unable to load private key", ex.getMessage());
    }

    @Test
    void rsaPrivateKeyReturnsKeyWhenRsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        KeyStore ks = mock(KeyStore.class);
        when(ks.getKey(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(pair.getPrivate());

        RSAPrivateKey key = jwtUtil.rsaPrivateKey(ks);
        assertNotNull(key);
    }

    @Test
    void rsaPublicKeyThrowsWhenKeyStoreThrows() throws Exception {
        KeyStore ks = mock(KeyStore.class);
        when(ks.getCertificate(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("bad"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPublicKey(ks));
        assertEquals("Unable to load RSA public key", ex.getMessage());
    }

    @Test
    void rsaPublicKeyReturnsKeyWhenRsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        Certificate cert = mock(Certificate.class);
        when(cert.getPublicKey()).thenReturn(pair.getPublic());
        KeyStore ks = mock(KeyStore.class);
        when(ks.getCertificate(org.mockito.ArgumentMatchers.any())).thenReturn(cert);

        RSAPublicKey key = jwtUtil.rsaPublicKey(ks);
        assertNotNull(key);
    }

    @Test
    void rsaPublicKeyThrowsWhenKeyNotRsa() {
        Certificate cert = mock(Certificate.class);
        when(cert.getPublicKey()).thenReturn(mock(java.security.PublicKey.class));
        KeyStore ks = mock(KeyStore.class);
        try {
            when(ks.getCertificate(org.mockito.ArgumentMatchers.any())).thenReturn(cert);
        } catch (Exception ignored) {}

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPublicKey(ks));
        assertEquals("Unable to load RSA public key", ex.getMessage());
    }
}
