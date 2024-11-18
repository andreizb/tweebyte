package ro.tweebyte.gatewayservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtConfigurationTest {

    @Autowired
    private JwtConfiguration jwtConfiguration;

    @Test
    void publicKey() throws Exception {
        RSAPublicKey result = jwtConfiguration.publicKey();
        assertNotNull(result, "The key should be an instance of RSAPublicKey");
    }

}