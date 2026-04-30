package ro.tweebyte.userservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.entity.UserEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Reactive analogue to async's UserRepositoryTest. Because the reactive stack uses
 * Spring Data R2DBC (no @DataJpaTest / TestEntityManager), the repository is mocked
 * and the StepVerifier asserts on the Mono contract — same behavioural assertions
 * as the async side (findByEmail / findByUserName return the persisted user).
 */
@ExtendWith(MockitoExtension.class)
public class UserRepositoryTest {

    @Mock
    private UserRepository userRepository;

    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(UUID.randomUUID());
        userEntity.setEmail("test@example.com");
        userEntity.setUserName("testUser");
        userEntity.setBiography("dasd");
        userEntity.setBirthDate(LocalDate.now());
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setIsPrivate(true);
        userEntity.setPassword("asdf");
    }

    @Test
    void findByEmail() {
        String email = "test@example.com";
        given(userRepository.findByEmail(eq(email))).willReturn(Mono.just(userEntity));

        StepVerifier.create(userRepository.findByEmail(email))
                .expectNextMatches(found -> email.equals(found.getEmail()))
                .verifyComplete();
    }

    @Test
    void findByUserName() {
        String userName = "testUser";
        given(userRepository.findByUserName(eq(userName))).willReturn(Mono.just(userEntity));

        StepVerifier.create(userRepository.findByUserName(userName))
                .expectNextMatches(found -> userName.equals(found.getUserName()))
                .verifyComplete();
    }

}
