package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class AuthenticationServiceTest {

    @InjectMocks
    private AuthenticationService authenticationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RSAPublicKey publicKey;

    @Mock
    private RSAPrivateKey privateKey;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    private final String userEmail = "test@example.com";
    private final String userPassword = "password";
    private final UserEntity userEntity = new UserEntity();

    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException {
        userEntity.setId(UUID.randomUUID());
        userEntity.setEmail(userEmail);
        userEntity.setPassword(userPassword);

        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(2048);
        KeyPair keyPair = keyGenerator.genKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        ReflectionTestUtils.setField(authenticationService, "publicKey", publicKey);
        ReflectionTestUtils.setField(authenticationService, "privateKey", privateKey);
    }

    @Test
    public void loginSuccess() {
        given(userRepository.findByEmail(anyString())).willReturn(Mono.just(userEntity));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        UserLoginRequest loginRequest = new UserLoginRequest(userEmail, userPassword);

        StepVerifier.create(authenticationService.login(loginRequest))
            .expectNextMatches(response -> response.getToken() != null && !response.getToken().isEmpty())
            .verifyComplete();
    }

    @Test
    public void loginFailure() {
        given(userRepository.findByEmail(anyString())).willReturn(Mono.just(userEntity));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        UserLoginRequest loginRequest = new UserLoginRequest(userEmail, "wrongPassword");

        StepVerifier.create(authenticationService.login(loginRequest))
            .expectError(AuthenticationException.class)
            .verify();
    }

    @Test
    public void registerSuccess() {
        given(userRepository.save(any(UserEntity.class))).willReturn(Mono.just(userEntity));
        given(userMapper.mapRequestToEntity(any())).willReturn(userEntity);

        UserRegisterRequest registerRequest = new UserRegisterRequest();
        registerRequest.setEmail(userEmail);
        registerRequest.setPassword(userPassword);

        StepVerifier.create(authenticationService.register(registerRequest))
            .expectNextMatches(response -> response.getToken() != null && !response.getToken().isEmpty())
            .verifyComplete();
    }
}
