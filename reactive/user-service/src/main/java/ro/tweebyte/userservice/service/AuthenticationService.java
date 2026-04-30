package ro.tweebyte.userservice.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.repository.UserRepository;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;

    private final RSAPublicKey publicKey;

    private final RSAPrivateKey privateKey;

    private final BCryptPasswordEncoder passwordEncoder;

    private final UserMapper userMapper;

    public Mono<AuthenticationResponse> login(UserLoginRequest request) {
        // an unknown email yields Mono.empty() from findByEmail, which without
        // switchIfEmpty produced a 200 with empty body (the controller's Mono completed
        // empty). Now we explicitly fail with AuthenticationException, which the
        // GlobalExceptionHandler maps to 401 — same shape as the password-mismatch path.
        return userRepository.findByEmail(request.getEmail())
            .switchIfEmpty(Mono.error(new AuthenticationException("Invalid username or password")))
            .flatMap(userEntity -> {
                if (!passwordEncoder.matches(request.getPassword(), userEntity.getPassword())) {
                    return Mono.error(new AuthenticationException("Invalid username or password"));
                }
                return Mono.just(handleTokenAuthentication(userEntity));
            });
    }

    @RateLimiter(name = "userServiceRateLimiter")
    public Mono<AuthenticationResponse> register(UserRegisterRequest request) {
        // pre-check unique constraints and surface a structured 400 via
        // UserAlreadyExistsException → GlobalExceptionHandler. Without this, the DB
        // layer's DataIntegrityViolation leaks to the client as a raw 500 with the
        // postgres `duplicate key value violates unique constraint "uk..."` message.
        // Matches async's behaviour.
        return userRepository.existsByEmail(request.getEmail())
            .flatMap(emailTaken -> {
                if (Boolean.TRUE.equals(emailTaken)) {
                    return Mono.error(new ro.tweebyte.userservice.exception.UserAlreadyExistsException(
                            "A user with this email already exists"));
                }
                return userRepository.existsByUserName(request.getUserName());
            })
            .flatMap(usernameTaken -> {
                if (Boolean.TRUE.equals(usernameTaken)) {
                    return Mono.error(new ro.tweebyte.userservice.exception.UserAlreadyExistsException(
                            "A user with this username already exists"));
                }
                return userRepository.save(userMapper.mapRequestToEntity(request));
            })
            .map(saved -> handleTokenAuthentication((UserEntity) saved));
    }

    private AuthenticationResponse handleTokenAuthentication(UserEntity userEntity) {
        return getAuthenticationResponse(userEntity.getId().toString(), userEntity.getEmail());
    }

    private AuthenticationResponse getAuthenticationResponse(String userId, String email) {
        Map<String, String> claims = new HashMap<>() {{
            put("user_id", userId);
            put("email", email);
        }};

        return new AuthenticationResponse().setToken(generateToken(userId, claims));
    }

    private String generateToken(String subject, Map<String, String> claims) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Instant.now().toEpochMilli());
        calendar.add(Calendar.DATE, 1);

        JWTCreator.Builder jwtBuilder = JWT.create().withSubject(subject);

        claims.forEach(jwtBuilder::withClaim);

        return jwtBuilder
            .withNotBefore(new Date())
            .withExpiresAt(calendar.getTime())
            .sign(Algorithm.RSA256(publicKey, privateKey));
    }

}
