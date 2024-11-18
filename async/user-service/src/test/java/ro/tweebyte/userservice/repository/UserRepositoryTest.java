package ro.tweebyte.userservice.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import ro.tweebyte.userservice.entity.UserEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail() {
        String email = "test@example.com";
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(email);
        userEntity.setBiography("dasd");
        userEntity.setBirthDate(LocalDate.now());
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setEmail(email);
        userEntity.setIsPrivate(true);
        userEntity.setUserName("test");
        userEntity.setPassword("asdf");

        entityManager.persist(userEntity);
        entityManager.flush();

        Optional<UserEntity> foundUser = userRepository.findByEmail(email);

        assertTrue(foundUser.isPresent());
        assertEquals(email, foundUser.get().getEmail());
    }

    @Test
    void findByUserName() {
        String email = "test@example.com";
        String userName = "testUser";
        UserEntity userEntity = new UserEntity();
        userEntity.setUserName(userName);
        userEntity.setBiography("dasd");
        userEntity.setBirthDate(LocalDate.now());
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setEmail(email);
        userEntity.setIsPrivate(true);
        userEntity.setPassword("asdf");

        entityManager.persist(userEntity);
        entityManager.flush();

        Optional<UserEntity> foundUser = userRepository.findByUserName(userName);

        assertTrue(foundUser.isPresent());
        assertEquals(userName, foundUser.get().getUserName());
    }

}