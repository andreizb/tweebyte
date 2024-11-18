package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InteractionClient interactionClient;

    @Mock
    private TweetClient tweetClient;

    @Mock
    private UserMapper userMapper;

    private final UUID userId = UUID.randomUUID();
    private UserEntity userEntity = new UserEntity();
    private UserDto userDto = new UserDto();
    private TweetDto tweetDto = new TweetDto();

    @BeforeEach
    public void setUp() {
        userEntity.setId(userId);
        userEntity.setEmail("test@example.com");
        userEntity.setPassword("password");

        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        given(userRepository.findById(eq(UUID.randomUUID()))).willReturn(Mono.empty());
        given(userRepository.findByUserName(any(String.class))).willReturn(Mono.just(userEntity));
        given(userRepository.searchUsers(any(String.class))).willReturn(Flux.just(userEntity));
        given(interactionClient.getFollowingCount(eq(userId), any())).willReturn(Mono.just(10L));
        given(interactionClient.getFollowersCount(eq(userId), any())).willReturn(Mono.just(20L));
        given(tweetClient.getUserTweets(eq(userId), any())).willReturn(Flux.just(tweetDto));
        given(userMapper.mapToProfileDto(eq(userEntity), any(Long.class), any(Long.class), any(List.class))).willReturn(userDto);
        given(userMapper.mapToSummaryDto(any(UserEntity.class))).willReturn(userDto);
    }

    @Test
    public void getUserProfileTest() {
        UUID userId = UUID.randomUUID();
        String authToken = "authToken";

        UserEntity mockUserEntity = new UserEntity();
        mockUserEntity.setId(userId);
        mockUserEntity.setUserName("testUser");

        Long followingCount = 10L;
        Long followersCount = 20L;

        TweetDto tweet1 = new TweetDto();
        tweet1.setId(UUID.randomUUID());
        tweet1.setContent("Tweet 1");

        TweetDto tweet2 = new TweetDto();
        tweet2.setId(UUID.randomUUID());
        tweet2.setContent("Tweet 2");

        List<TweetDto> tweetList = List.of(tweet1, tweet2);

        UserDto expectedUserDto = new UserDto();
        expectedUserDto.setId(userId);
        expectedUserDto.setUserName("testUser");
        expectedUserDto.setFollowing(followingCount);
        expectedUserDto.setFollowers(followersCount);
        expectedUserDto.setTweets(tweetList);

        when(userRepository.findById(userId))
                .thenReturn(Mono.just(mockUserEntity));

        when(interactionClient.getFollowingCount(userId, authToken))
                .thenReturn(Mono.just(followingCount));

        when(interactionClient.getFollowersCount(userId, authToken))
                .thenReturn(Mono.just(followersCount));

        when(tweetClient.getUserTweets(userId, authToken))
                .thenReturn(Flux.fromIterable(tweetList));

        when(userMapper.mapToProfileDto(mockUserEntity, followingCount, followersCount, tweetList))
                .thenReturn(expectedUserDto);

        StepVerifier.create(userService.getUserProfile(userId, authToken))
                .expectNext(expectedUserDto)
                .verifyComplete();

        verify(userRepository).findById(userId);
        verify(interactionClient).getFollowingCount(userId, authToken);
        verify(interactionClient).getFollowersCount(userId, authToken);
        verify(tweetClient).getUserTweets(userId, authToken);
        verify(userMapper).mapToProfileDto(mockUserEntity, followingCount, followersCount, tweetList);
    }

    @Test
    public void getUserSummaryTest() {
        StepVerifier.create(userService.getUserSummary(userId))
            .expectNext(userDto)
            .verifyComplete();
    }

    @Test
    public void getUserSummaryByUserNameTest() {
        StepVerifier.create(userService.getUserSummaryByUserName("username"))
            .expectNext(userDto)
            .verifyComplete();
    }

    @Test
    public void searchUserTest() {
        StepVerifier.create(userService.searchUser("searchTerm"))
            .expectNext(userDto)
            .verifyComplete();
    }

    @Test
    public void updateUserTest() {
        given(userRepository.save(any(UserEntity.class))).willReturn(Mono.just(userEntity));
        UserUpdateRequest request = new UserUpdateRequest();

        StepVerifier.create(userService.updateUser(userId, request))
            .verifyComplete();
    }

}
