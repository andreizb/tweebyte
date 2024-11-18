package ro.tweebyte.tweetservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilTest {

	@InjectMocks
	private JwtUtil jwtUtil;

	@Mock
	private KeyStore keyStore;

	@Mock
	private Certificate certificate;

	@Mock
	private RSAPublicKey rsaPublicKey;

	private String keyAlias = "jwt";

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testJwtDecoderCreation() {
		JwtDecoder jwtDecoder = jwtUtil.jwtDecoder(rsaPublicKey);

		assertNotNull(jwtDecoder);
		assertTrue(jwtDecoder instanceof NimbusJwtDecoder);
	}

	@Test
	void testRsaPublicKeyThrowsExceptionIfKeyNotRSAPublicKey() throws Exception {
		PublicKey publicKey = mock(PublicKey.class);
		when(keyStore.getCertificate(keyAlias)).thenReturn(certificate);
		when(certificate.getPublicKey()).thenReturn(publicKey);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPublicKey(keyStore));
		assertEquals("Unable to load RSA public key", exception.getMessage());
	}

	@Test
	void testRsaPublicKeyThrowsExceptionIfCertificateIsNull() throws Exception {
		when(keyStore.getCertificate(keyAlias)).thenReturn(null);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> jwtUtil.rsaPublicKey(keyStore));
		assertEquals("Unable to load RSA public key", exception.getMessage());
	}

}