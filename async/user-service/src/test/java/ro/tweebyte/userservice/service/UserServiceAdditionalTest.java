package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceAdditionalTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InteractionClient interactionClient;

    @Mock
    private TweetClient tweetClient;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ExecutorService executorService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(userService, "executorService", Executors.newFixedThreadPool(1));
    }

    @Test
    void getUserSummaryThrowsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CompletionException ex = assertThrows(CompletionException.class,
            () -> userService.getUserSummary(userId).join());
        assertTrue(ex.getCause() instanceof UserNotFoundException);
    }

    @Test
    void getUserSummaryByUserNameThrowsWhenUserNotFound() {
        when(userRepository.findByUserName("missing")).thenReturn(Optional.empty());

        CompletionException ex = assertThrows(CompletionException.class,
            () -> userService.getUserSummaryByUserName("missing").join());
        assertTrue(ex.getCause() instanceof UserNotFoundException);
    }

    @Test
    void updateUserThrowsWhenEmailAlreadyExists() {
        UUID userId = UUID.randomUUID();
        UserUpdateRequest req = new UserUpdateRequest();
        req.setEmail("dup@example.com");

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        CompletionException ex = assertThrows(CompletionException.class,
            () -> userService.updateUser(userId, req).join());
        assertTrue(ex.getCause() instanceof UserAlreadyExistsException);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserThrowsWhenUserNameAlreadyExists() {
        UUID userId = UUID.randomUUID();
        UserUpdateRequest req = new UserUpdateRequest();
        req.setUserName("takenname");

        when(userRepository.existsByUserName("takenname")).thenReturn(true);

        CompletionException ex = assertThrows(CompletionException.class,
            () -> userService.updateUser(userId, req).join());
        assertTrue(ex.getCause() instanceof UserAlreadyExistsException);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserThrowsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        UserUpdateRequest req = new UserUpdateRequest();
        // both null -> skip duplicate checks, proceed to findById which returns empty

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CompletionException ex = assertThrows(CompletionException.class,
            () -> userService.updateUser(userId, req).join());
        assertTrue(ex.getCause() instanceof UserNotFoundException);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserWithEmailAndNameAndExistingUser() {
        UUID userId = UUID.randomUUID();
        UserUpdateRequest req = new UserUpdateRequest();
        req.setEmail("e@e.com");
        req.setUserName("n");
        UserEntity entity = new UserEntity();

        when(userRepository.existsByEmail("e@e.com")).thenReturn(false);
        when(userRepository.existsByUserName("n")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));

        userService.updateUser(userId, req).join();

        verify(userMapper).mapRequestToEntity(req, entity);
        verify(userRepository).save(entity);
    }
}
