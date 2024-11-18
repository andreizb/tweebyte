package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
public class CustomUserDetailsServiceTest {

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UserRepository userRepository;

    private final UUID userId = UUID.randomUUID();
    private final String userEmail = "test@example.com";
    private final String userPassword = "password";
    private UserEntity userEntity;

    @BeforeEach
    public void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setEmail(userEmail);
        userEntity.setPassword(userPassword);

        given(userRepository.findById(any(UUID.class))).willReturn(Mono.empty());
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
    }

    @Test
    public void findByUsernameWhenUserExists() {
        StepVerifier.create(customUserDetailsService.findByUsername(userId.toString()))
            .assertNext(userDetails -> {
                assert userDetails.getUsername().equals(userEmail);
                assert userDetails.getPassword().equals(userPassword);
            })
            .verifyComplete();
    }

    @Test
    public void findByUsernameWhenUserDoesNotExist() {
        StepVerifier.create(customUserDetailsService.findByUsername(UUID.randomUUID().toString()))
            .expectError(UsernameNotFoundException.class)
            .verify();
    }
}
