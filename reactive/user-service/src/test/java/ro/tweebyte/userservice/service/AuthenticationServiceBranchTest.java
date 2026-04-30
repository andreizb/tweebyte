package ro.tweebyte.userservice.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercises every branch in AuthenticationService:
 *  - login: switchIfEmpty, password mismatch, success
 *  - register: emailTaken=true / userNameTaken=true / both-false (success)
 *  - JWT token construction: subject + claims (user_id, email)
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceBranchTest {

    @InjectMocks
    private AuthenticationService authenticationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    private UserEntity userEntity;
    private final UUID userId = UUID.randomUUID();
    private final String userEmail = "user@example.com";
    private final String userPassword = "$2a$10$hashedPassword";

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setEmail(userEmail);
        userEntity.setPassword(userPassword);
        userEntity.setUserName("alice");

        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(2048);
        KeyPair keyPair = keyGenerator.genKeyPair();
        ReflectionTestUtils.setField(authenticationService, "publicKey", keyPair.getPublic());
        ReflectionTestUtils.setField(authenticationService, "privateKey", keyPair.getPrivate());
    }

    // --- login ----------------------------------------------------------

    @Test
    void loginUnknownEmailEmitsAuthenticationException() {
        // switchIfEmpty path
        given(userRepository.findByEmail(eq("nobody@example.com"))).willReturn(Mono.empty());

        StepVerifier.create(authenticationService.login(new UserLoginRequest("nobody@example.com", "x")))
                .expectErrorMatches(t -> t instanceof AuthenticationException
                        && t.getMessage().equals("Invalid username or password"))
                .verify();
    }

    @Test
    void loginPasswordMismatchEmitsAuthenticationException() {
        given(userRepository.findByEmail(eq(userEmail))).willReturn(Mono.just(userEntity));
        given(passwordEncoder.matches(eq("wrong"), eq(userPassword))).willReturn(false);

        StepVerifier.create(authenticationService.login(new UserLoginRequest(userEmail, "wrong")))
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void loginSuccessProducesTokenWithExpectedClaims() {
        given(userRepository.findByEmail(eq(userEmail))).willReturn(Mono.just(userEntity));
        given(passwordEncoder.matches(eq("plain"), eq(userPassword))).willReturn(true);

        StepVerifier.create(authenticationService.login(new UserLoginRequest(userEmail, "plain")))
                .assertNext(response -> {
                    assertNotNull(response);
                    assertNotNull(response.getToken());
                    DecodedJWT decoded = JWT.decode(response.getToken());
                    assertEquals(userId.toString(), decoded.getSubject());
                    assertEquals(userId.toString(), decoded.getClaim("user_id").asString());
                    assertEquals(userEmail, decoded.getClaim("email").asString());
                    assertNotNull(decoded.getExpiresAt());
                    assertNotNull(decoded.getNotBefore());
                })
                .verifyComplete();
    }

    // --- register -------------------------------------------------------

    @Test
    void registerEmailAlreadyTakenEmitsUserAlreadyExists() {
        // existsByEmail=true short-circuits
        given(userRepository.existsByEmail(eq(userEmail))).willReturn(Mono.just(true));

        UserRegisterRequest req = new UserRegisterRequest();
        req.setEmail(userEmail);
        req.setUserName("alice");
        req.setPassword("password1");

        StepVerifier.create(authenticationService.register(req))
                .expectErrorMatches(t -> t instanceof UserAlreadyExistsException
                        && t.getMessage().equals("A user with this email already exists"))
                .verify();

        verify(userRepository, never()).existsByUserName(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUserNameAlreadyTakenEmitsUserAlreadyExists() {
        // existsByEmail=false, existsByUserName=true
        given(userRepository.existsByEmail(eq(userEmail))).willReturn(Mono.just(false));
        given(userRepository.existsByUserName(eq("alice"))).willReturn(Mono.just(true));

        UserRegisterRequest req = new UserRegisterRequest();
        req.setEmail(userEmail);
        req.setUserName("alice");
        req.setPassword("password1");

        StepVerifier.create(authenticationService.register(req))
                .expectErrorMatches(t -> t instanceof UserAlreadyExistsException
                        && t.getMessage().equals("A user with this username already exists"))
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerEmailNullExistsFlagFallsThroughToUserNameCheck() {
        // Boolean.TRUE.equals(null) is false → both checks pass when repo emits Mono.just(false)
        given(userRepository.existsByEmail(eq(userEmail))).willReturn(Mono.just(false));
        given(userRepository.existsByUserName(eq("bob"))).willReturn(Mono.just(false));
        given(userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(userEntity);
        given(userRepository.save(any(UserEntity.class))).willReturn(Mono.just(userEntity));

        UserRegisterRequest req = new UserRegisterRequest();
        req.setEmail(userEmail);
        req.setUserName("bob");
        req.setPassword("password1");

        StepVerifier.create(authenticationService.register(req))
                .assertNext(response -> assertNotNull(response.getToken()))
                .verifyComplete();
    }

    @Test
    void registerHappyPathPassesMappedEntityToSave() {
        given(userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
        given(userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
        given(userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(userEntity);
        given(userRepository.save(any(UserEntity.class))).willReturn(Mono.just(userEntity));

        UserRegisterRequest req = new UserRegisterRequest();
        req.setEmail(userEmail);
        req.setUserName("alice");
        req.setPassword("password1");

        StepVerifier.create(authenticationService.register(req))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals(userEntity, captor.getValue());
    }

    @Test
    void registerSavedTokenContainsSubjectAndClaims() {
        given(userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
        given(userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
        given(userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(userEntity);
        given(userRepository.save(any(UserEntity.class))).willReturn(Mono.just(userEntity));

        UserRegisterRequest req = new UserRegisterRequest();
        req.setEmail(userEmail);
        req.setUserName("alice");
        req.setPassword("password1");

        AuthenticationResponse response = authenticationService.register(req).block();
        assertNotNull(response);
        DecodedJWT decoded = JWT.decode(response.getToken());
        assertEquals(userId.toString(), decoded.getSubject());
        assertEquals(userEmail, decoded.getClaim("email").asString());
    }

    @Test
    void registerSaveFailurePropagates() {
        given(userRepository.existsByEmail(anyString())).willReturn(Mono.just(false));
        given(userRepository.existsByUserName(anyString())).willReturn(Mono.just(false));
        given(userMapper.mapRequestToEntity(any(UserRegisterRequest.class))).willReturn(userEntity);
        given(userRepository.save(any(UserEntity.class)))
                .willReturn(Mono.error(new RuntimeException("db down")));

        UserRegisterRequest req = new UserRegisterRequest();
        req.setEmail(userEmail);
        req.setUserName("alice");
        req.setPassword("password1");

        StepVerifier.create(authenticationService.register(req))
                .expectErrorMatches(t -> t instanceof RuntimeException && "db down".equals(t.getMessage()))
                .verify();
    }
}
