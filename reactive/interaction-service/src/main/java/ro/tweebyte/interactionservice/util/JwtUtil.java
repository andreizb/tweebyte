package ro.tweebyte.interactionservice.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

@Component
public class JwtUtil {

    @Value("${app.security.jwt.keystore-location}")
    private String keyStorePath;

    @Value("${app.security.jwt.keystore-password}")
    private String keyStorePassword;

    @Value("${app.security.jwt.key-alias}")
    private String keyAlias;

    @Bean
    public KeyStore keyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(keyStorePath);
            keyStore.load(resourceAsStream, keyStorePassword.toCharArray());
            return keyStore;
        } catch (Exception ignored) {
            // TODO do later
//            log.error("Unable to load keystore: {}", keyStorePath, e);
        }

        throw new IllegalArgumentException("Unable to load keystore");
    }

    @Bean
    public RSAPublicKey rsaPublicKey(KeyStore keyStore) {
        try {
            Certificate certificate = keyStore.getCertificate(keyAlias);
            PublicKey publicKey = certificate.getPublicKey();

            if (publicKey instanceof RSAPublicKey) {
                return (RSAPublicKey) publicKey;
            }
        } catch (Exception ignored) {
            // TODO do later
//            log.error("Unable to load private key from keystore: {}", keyStorePath, e);
        }

        throw new IllegalArgumentException("Unable to load RSA public key");
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        return NimbusJwtDecoder
            .withPublicKey(rsaPublicKey)
            .build();
    }

}
