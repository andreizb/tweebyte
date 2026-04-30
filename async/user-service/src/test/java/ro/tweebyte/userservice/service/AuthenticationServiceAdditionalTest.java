package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceAdditionalTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder encoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RSAPublicKey publicKey;

    @Mock
    private RSAPrivateKey privateKey;

    @InjectMocks
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(2048);
        KeyPair keyPair = keyGenerator.genKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        ReflectionTestUtils.setField(authenticationService, "executorService", Executors.newFixedThreadPool(1));
        ReflectionTestUtils.setField(authenticationService, "publicKey", publicKey);
        ReflectionTestUtils.setField(authenticationService, "privateKey", privateKey);
    }

    @Test
    void loginWithWrongPasswordThrowsAuthenticationException() {
        UserLoginRequest request = new UserLoginRequest("user@example.com", "wrong");
        UserEntity entity = new UserEntity();
        entity.setEmail("user@example.com");
        entity.setPassword("hashed");
        entity.setId(UUID.randomUUID());

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(entity));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        CompletionException ex = assertThrows(CompletionException.class,
            () -> authenticationService.login(request).join());
        assert ex.getCause() instanceof AuthenticationException;
    }

    @Test
    void registerThrowsWhenEmailAlreadyExists() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setEmail("existing@example.com");
        request.setUserName("name");
        request.setPassword("pwd");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        CompletionException ex = assertThrows(CompletionException.class,
            () -> authenticationService.register(request).join());
        assert ex.getCause() instanceof UserAlreadyExistsException;
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registerThrowsWhenUserNameAlreadyExists() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setEmail("new@example.com");
        request.setUserName("takenname");
        request.setPassword("pwd");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUserName("takenname")).thenReturn(true);

        CompletionException ex = assertThrows(CompletionException.class,
            () -> authenticationService.register(request).join());
        assert ex.getCause() instanceof UserAlreadyExistsException;
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
