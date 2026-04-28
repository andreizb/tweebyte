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
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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
    void getUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity mockUserEntity = new UserEntity();
        UserDto mockUserDto = new UserDto();
        long followersCount = 100L;
        long followingCount = 50L;
        List<TweetDto> mockTweetPage = List.of();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUserEntity));
        when(interactionClient.getFollowingCount(userId, "AUTH_TOKEN")).thenReturn(CompletableFuture.completedFuture(followingCount));
        when(interactionClient.getFollowersCount(userId, "AUTH_TOKEN")).thenReturn(CompletableFuture.completedFuture(followersCount));
        when(tweetClient.getUserTweets(userId, "AUTH_TOKEN")).thenReturn(CompletableFuture.completedFuture(mockTweetPage));
        when(userMapper.mapToProfileDto(any(UserEntity.class), anyLong(), anyLong(), any(List.class))).thenReturn(mockUserDto);

        CompletableFuture<UserDto> result = userService.getUserProfile(userId, "AUTH_TOKEN");
        UserDto resultDto = result.join();

        assertNotNull(resultDto);
        verify(userRepository).findById(userId);
        verify(interactionClient).getFollowingCount(userId, "AUTH_TOKEN");
        verify(interactionClient).getFollowersCount(userId, "AUTH_TOKEN");
        verify(tweetClient).getUserTweets(userId, "AUTH_TOKEN");
        verify(userMapper).mapToProfileDto(any(UserEntity.class), eq(followingCount), eq(followersCount), eq(mockTweetPage));
    }

    @Test
    void getUserSummary() {
        UUID userId = UUID.randomUUID();
        UserEntity mockUserEntity = new UserEntity();
        UserDto mockUserDto = new UserDto();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUserEntity));
        when(userMapper.mapToSummaryDto(mockUserEntity)).thenReturn(mockUserDto);

        CompletableFuture<UserDto> result = userService.getUserSummary(userId);
        UserDto resultDto = result.join();

        assertNotNull(resultDto);
        verify(userRepository).findById(userId);
        verify(userMapper).mapToSummaryDto(mockUserEntity);
    }

    @Test
    void getUserSummaryByUserName() {
        String userName = "testUser";
        UserEntity mockUserEntity = new UserEntity();
        UserDto mockUserDto = new UserDto();

        when(userRepository.findByUserName(userName)).thenReturn(Optional.of(mockUserEntity));
        when(userMapper.mapToSummaryDto(mockUserEntity)).thenReturn(mockUserDto);

        CompletableFuture<UserDto> result = userService.getUserSummaryByUserName(userName);
        UserDto resultDto = result.join();

        assertNotNull(resultDto);
        verify(userRepository).findByUserName(userName);
        verify(userMapper).mapToSummaryDto(mockUserEntity);
    }

    @Test
    void searchUser() {
        String searchTerm = "search";
        List<UserEntity> mockUserPage = List.of();

        when(userRepository.searchUsers(any())).thenReturn(mockUserPage);

        CompletableFuture<List<UserDto>> result = userService.searchUser(searchTerm);
        List<UserDto> resultPage = result.join();

        assertNotNull(resultPage);
        verify(userRepository).searchUsers('%' + searchTerm + '%');
    }

    @Test
    void updateUser() {
        UUID userId = UUID.randomUUID();
        UserUpdateRequest mockUserUpdateRequest = new UserUpdateRequest();
        UserEntity mockUserEntity = new UserEntity();

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUserEntity));
        doAnswer(invocation -> null).when(userRepository).save(any(UserEntity.class));

        CompletableFuture<Void> result = userService.updateUser(userId, mockUserUpdateRequest);
        result.join();

        verify(userRepository).findById(userId);
        verify(userRepository).save(mockUserEntity);
    }

}