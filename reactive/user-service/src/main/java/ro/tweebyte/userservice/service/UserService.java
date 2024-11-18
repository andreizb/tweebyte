package ro.tweebyte.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final InteractionClient interactionClient;
    private final TweetClient tweetClient;
    private final UserMapper userMapper;

    public Mono<UserDto> getUserProfile(UUID userId, String authToken) {
        Mono<UserEntity> userMono = userRepository.findById(userId)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found for id: " + userId)));

        Mono<Long> followingMono = interactionClient.getFollowingCount(userId, authToken);
        Mono<Long> followersMono = interactionClient.getFollowersCount(userId, authToken);
        Flux<TweetDto> tweetsFlux = tweetClient.getUserTweets(userId, authToken);

        return Mono.zip(userMono, followingMono, followersMono, tweetsFlux.collectList())
            .map(tuple -> {
                UserEntity user = tuple.getT1();
                Long following = tuple.getT2();
                Long followers = tuple.getT3();
                List<TweetDto> tweets = tuple.getT4();
                return userMapper.mapToProfileDto(user, following, followers, tweets);
            });
    }

    public Mono<UserDto> getUserSummary(UUID userId) {
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found for id: " + userId)))
            .map(userMapper::mapToSummaryDto);
    }

    public Mono<UserDto> getUserSummaryByUserName(String userName) {
        return userRepository.findByUserName(userName)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found for name: " + userName)))
            .map(userMapper::mapToSummaryDto);
    }

    public Flux<UserDto> searchUser(String searchTerm) {
        return userRepository.searchUsers(searchTerm)
            .map(userMapper::mapToSummaryDto);
    }

    public Mono<Void> updateUser(UUID userId, UserUpdateRequest userUpdateRequest) {
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found for id: " + userId)))
            .flatMap(user -> {
                userMapper.mapRequestToEntity(userUpdateRequest, user);
                return userRepository.save(user);
            }).retryWhen(
                Retry.backoff(3, Duration.ofSeconds(1))
                    .filter(throwable -> !(throwable instanceof UserNotFoundException))
            ).then();
    }

}
