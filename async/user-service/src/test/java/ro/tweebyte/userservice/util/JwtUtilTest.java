package ro.tweebyte.userservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

	@InjectMocks
	private JwtUtil jwtUtil;

	@Mock
	private RSAPublicKey mockRSAPublicKey;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testJwtDecoderCreation() {
		// Act
		JwtDecoder jwtDecoder = jwtUtil.jwtDecoder(mockRSAPublicKey);

		// Assert
		assertNotNull(jwtDecoder);
		assertTrue(jwtDecoder instanceof NimbusJwtDecoder);
	}

	@Test
	void testBCryptPasswordEncoder() {
		// Act
		BCryptPasswordEncoder encoder = jwtUtil.bCryptPasswordEncoder();

		// Assert
		assertNotNull(encoder);
	}
}