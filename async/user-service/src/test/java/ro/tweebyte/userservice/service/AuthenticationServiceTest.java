package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.AuthenticationResponse;
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
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

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
    void testLoginSuccess() throws ExecutionException, InterruptedException {
        UserLoginRequest request = new UserLoginRequest("user@example.com", "correctpassword");
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail("user@example.com");
        userEntity.setPassword("$2a$10$SomeHashedPasswordHere");
        userEntity.setId(UUID.randomUUID());

        when(userRepository.findByEmail(any())).thenReturn(Optional.of(userEntity));
        when(encoder.matches(request.getPassword(), userEntity.getPassword())).thenReturn(true);

        AuthenticationResponse result = authenticationService.login(request).get();

        assertNotNull(result);
        assertFalse(result.getToken().isEmpty());
        verify(userRepository).findByEmail(request.getEmail());
        verify(encoder).matches(request.getPassword(), userEntity.getPassword());
    }

    @Test
    void testLoginFailure() {
        UserLoginRequest request = new UserLoginRequest("user@example.com", "wrongpassword");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(ExecutionException.class, () -> {
            CompletableFuture<AuthenticationResponse> result = authenticationService.login(request);
            result.get();
        });

        verify(userRepository).findByEmail(request.getEmail());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void testRegisterSuccess() throws ExecutionException, InterruptedException {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("newpassword");
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail("newuser@example.com");
        userEntity.setPassword("$2a$10$SomeHashedPasswordHere");
        userEntity.setId(UUID.randomUUID());

        when(userMapper.mapRequestToEntity(request)).thenReturn(userEntity);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);

        CompletableFuture<AuthenticationResponse> result = authenticationService.register(request);

        assertNotNull(result.get());
        assertFalse(result.get().getToken().isEmpty());
        verify(userRepository).save(any(UserEntity.class));
        verify(userMapper).mapRequestToEntity(request);
    }

}