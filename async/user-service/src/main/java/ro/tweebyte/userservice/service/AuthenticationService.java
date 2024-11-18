package ro.tweebyte.userservice.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.AuthenticationException;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;

    private final RSAPublicKey publicKey;

    private final RSAPrivateKey privateKey;

    private final UserMapper userMapper;

    private final BCryptPasswordEncoder encoder;

    @Qualifier(value = "authenticationExecutorService")
    private final ExecutorService executorService;

    public CompletableFuture<AuthenticationResponse> login(UserLoginRequest request) {
        return CompletableFuture.supplyAsync(
            () -> userRepository.findByEmail(request.getEmail())/*,*/
//                executorService
            ).thenApply(optional -> {
                if (optional.isEmpty() || !encoder.matches(request.getPassword(), optional.get().getPassword())) {
                    throw new AuthenticationException("Invalid username or password");
                }
                return optional.get();
            }).thenApply(this::handleTokenAuthentication);
    }

    public CompletableFuture<AuthenticationResponse> register(UserRegisterRequest request) {
        return CompletableFuture.supplyAsync(() -> {
                if (userRepository.existsByEmail(request.getEmail())) {
                    throw new UserAlreadyExistsException("A user with this email already exists");
                }

                if (userRepository.existsByUserName(request.getUserName())) {
                    throw new UserAlreadyExistsException("A user with this username already exists");
                }

                return userRepository.save(userMapper.mapRequestToEntity(request));
            }, executorService).thenApply(this::handleTokenAuthentication);
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
