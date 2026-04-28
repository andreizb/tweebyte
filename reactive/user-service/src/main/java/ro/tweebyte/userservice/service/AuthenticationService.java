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
        return userRepository.findByEmail(request.getEmail())
            .flatMap(userEntity -> {
                if (userEntity != null) {
                    if (!passwordEncoder.matches(request.getPassword(), userEntity.getPassword())) {
                        return Mono.error(new AuthenticationException("Invalid username or password"));
                    }
                    return Mono.just(handleTokenAuthentication(userEntity));
                } else {
                    return Mono.error(new AuthenticationException("Invalid username or password"));
                }
            });
    }

    @RateLimiter(name = "userServiceRateLimiter")
    public Mono<AuthenticationResponse> register(UserRegisterRequest request) {
        return userRepository.save(userMapper.mapRequestToEntity(request))
            .map(this::handleTokenAuthentication);
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
