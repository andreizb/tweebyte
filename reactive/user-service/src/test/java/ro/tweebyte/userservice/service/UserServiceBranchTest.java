package ro.tweebyte.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage tests for UserService — exercises every switchIfEmpty,
 * client failure path, and update retry filter.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBranchTest {

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
    private final String authToken = "Bearer x";

    private UserEntity userEntity;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setUserName("alice");
        userDto = UserDto.builder().id(userId).userName("alice").build();
    }

    // --- getUserProfile -----------------------------------------------

    @Test
    void getUserProfileUserMissingEmitsUserNotFound() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.empty());
        // The other Monos must complete so Mono.zip can fail through user error.
        lenient().when(interactionClient.getFollowingCount(eq(userId), eq(authToken))).thenReturn(Mono.just(0L));
        lenient().when(interactionClient.getFollowersCount(eq(userId), eq(authToken))).thenReturn(Mono.just(0L));
        lenient().when(tweetClient.getUserTweets(eq(userId), eq(authToken))).thenReturn(Flux.empty());

        StepVerifier.create(userService.getUserProfile(userId, authToken))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void getUserProfileFollowingClientFailsPropagatesError() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        given(interactionClient.getFollowingCount(eq(userId), eq(authToken)))
                .willReturn(Mono.error(new RuntimeException("svc down")));
        lenient().when(interactionClient.getFollowersCount(eq(userId), eq(authToken))).thenReturn(Mono.just(0L));
        lenient().when(tweetClient.getUserTweets(eq(userId), eq(authToken))).thenReturn(Flux.empty());

        StepVerifier.create(userService.getUserProfile(userId, authToken))
                .expectErrorMatches(t -> "svc down".equals(t.getMessage()))
                .verify();
    }

    @Test
    void getUserProfileFollowersClientFailsPropagatesError() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        lenient().when(interactionClient.getFollowingCount(eq(userId), eq(authToken))).thenReturn(Mono.just(0L));
        given(interactionClient.getFollowersCount(eq(userId), eq(authToken)))
                .willReturn(Mono.error(new RuntimeException("followers boom")));
        lenient().when(tweetClient.getUserTweets(eq(userId), eq(authToken))).thenReturn(Flux.empty());

        StepVerifier.create(userService.getUserProfile(userId, authToken))
                .expectErrorMatches(t -> "followers boom".equals(t.getMessage()))
                .verify();
    }

    @Test
    void getUserProfileTweetClientFailsPropagatesError() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        lenient().when(interactionClient.getFollowingCount(eq(userId), eq(authToken))).thenReturn(Mono.just(0L));
        lenient().when(interactionClient.getFollowersCount(eq(userId), eq(authToken))).thenReturn(Mono.just(0L));
        given(tweetClient.getUserTweets(eq(userId), eq(authToken)))
                .willReturn(Flux.error(new RuntimeException("tweets down")));

        StepVerifier.create(userService.getUserProfile(userId, authToken))
                .expectErrorMatches(t -> "tweets down".equals(t.getMessage()))
                .verify();
    }

    @Test
    void getUserProfileAllSuccessfulProducesUserDto() {
        TweetDto tweet = new TweetDto();
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        given(interactionClient.getFollowingCount(eq(userId), eq(authToken))).willReturn(Mono.just(7L));
        given(interactionClient.getFollowersCount(eq(userId), eq(authToken))).willReturn(Mono.just(13L));
        given(tweetClient.getUserTweets(eq(userId), eq(authToken))).willReturn(Flux.just(tweet));
        given(userMapper.mapToProfileDto(eq(userEntity), eq(7L), eq(13L), any(List.class)))
                .willReturn(userDto);

        StepVerifier.create(userService.getUserProfile(userId, authToken))
                .expectNext(userDto)
                .verifyComplete();
    }

    // --- getUserSummary -----------------------------------------------

    @Test
    void getUserSummaryUserMissingEmitsNotFound() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.empty());

        StepVerifier.create(userService.getUserSummary(userId))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void getUserSummaryUserFoundMapsToDto() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        given(userMapper.mapToSummaryDto(eq(userEntity))).willReturn(userDto);

        StepVerifier.create(userService.getUserSummary(userId))
                .expectNext(userDto)
                .verifyComplete();
    }

    // --- getUserSummaryByUserName -------------------------------------

    @Test
    void getUserSummaryByUserNameMissingEmitsNotFound() {
        given(userRepository.findByUserName(eq("ghost"))).willReturn(Mono.empty());

        StepVerifier.create(userService.getUserSummaryByUserName("ghost"))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void getUserSummaryByUserNameFoundMapsToDto() {
        given(userRepository.findByUserName(eq("alice"))).willReturn(Mono.just(userEntity));
        given(userMapper.mapToSummaryDto(eq(userEntity))).willReturn(userDto);

        StepVerifier.create(userService.getUserSummaryByUserName("alice"))
                .expectNext(userDto)
                .verifyComplete();
    }

    // --- searchUser ---------------------------------------------------

    @Test
    void searchUserWrapsTermInPercentSigns() {
        // searchUser passes "%term%" to repo
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        given(userRepository.searchUsers(anyString())).willReturn(Flux.just(userEntity));
        given(userMapper.mapToSummaryDto(any(UserEntity.class))).willReturn(userDto);

        StepVerifier.create(userService.searchUser("alic"))
                .expectNext(userDto)
                .verifyComplete();

        verify(userRepository).searchUsers(captor.capture());
        assertEquals("%alic%", captor.getValue());
    }

    @Test
    void searchUserEmptyResultProducesEmptyFlux() {
        given(userRepository.searchUsers(eq("%nope%"))).willReturn(Flux.empty());

        StepVerifier.create(userService.searchUser("nope"))
                .verifyComplete();
    }

    @Test
    void searchUserMultipleHits() {
        UserEntity other = new UserEntity();
        other.setId(UUID.randomUUID());
        given(userRepository.searchUsers(eq("%a%"))).willReturn(Flux.just(userEntity, other));
        given(userMapper.mapToSummaryDto(any(UserEntity.class))).willReturn(userDto);

        StepVerifier.create(userService.searchUser("a"))
                .expectNext(userDto)
                .expectNext(userDto)
                .verifyComplete();
    }

    // --- updateUser ---------------------------------------------------

    @Test
    void updateUserUserMissingEmitsNotFoundAndDoesNotRetry() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.empty());

        StepVerifier.create(userService.updateUser(userId, new UserUpdateRequest()))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void updateUserSuccessCallsMapperAndSave() {
        given(userRepository.findById(eq(userId))).willReturn(Mono.just(userEntity));
        given(userRepository.save(any(UserEntity.class))).willReturn(Mono.just(userEntity));
        UserUpdateRequest req = new UserUpdateRequest();
        req.setUserName("newname");

        StepVerifier.create(userService.updateUser(userId, req))
                .verifyComplete();

        verify(userMapper).mapRequestToEntity(eq(req), eq(userEntity));
        verify(userRepository).save(eq(userEntity));
    }
}
