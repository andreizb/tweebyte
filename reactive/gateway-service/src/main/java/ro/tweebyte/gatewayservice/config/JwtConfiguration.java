package ro.tweebyte.gatewayservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtConfiguration {

    @Value("${app.security.jwt.keystore-location}")
    private String keyStorePath;

    @Value("${app.security.jwt.keystore-password}")
    private String keyStorePassword;

    @Value("${app.security.jwt.key-alias}")
    private String keyAlias;

    @Bean
    public RSAPublicKey publicKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(keyStorePath);
        keyStore.load(resourceAsStream, keyStorePassword.toCharArray());

        Certificate cert = keyStore.getCertificate(keyAlias);
        PublicKey publicKey = cert.getPublicKey();
        return (RSAPublicKey) publicKey;
    }

}
