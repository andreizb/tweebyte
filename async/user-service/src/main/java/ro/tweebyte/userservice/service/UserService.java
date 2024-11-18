package ro.tweebyte.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import ro.tweebyte.userservice.client.InteractionClient;
import ro.tweebyte.userservice.client.TweetClient;
import ro.tweebyte.userservice.entity.UserEntity;
import ro.tweebyte.userservice.exception.UserAlreadyExistsException;
import ro.tweebyte.userservice.exception.UserException;
import ro.tweebyte.userservice.exception.UserNotFoundException;
import ro.tweebyte.userservice.mapper.UserMapper;
import ro.tweebyte.userservice.model.TweetDto;
import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final InteractionClient interactionClient;

    private final TweetClient tweetClient;

    private final UserMapper userMapper;

    @Qualifier(value = "userExecutorService")
    private final ExecutorService executorService;

    @SneakyThrows
    public CompletableFuture<UserDto> getUserProfile(UUID userId, String authToken) {
        CompletableFuture<UserEntity> userFuture = CompletableFuture.supplyAsync(
            () -> userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + userId)),
            executorService
        );

        CompletableFuture<Long> followingFuture = interactionClient.getFollowingCount(userId, authToken);
        CompletableFuture<Long> followersFuture = interactionClient.getFollowersCount(userId, authToken);
        CompletableFuture<List<TweetDto>> tweetsFuture = tweetClient.getUserTweets(userId, authToken);

        return CompletableFuture.allOf(userFuture, followingFuture, followersFuture)
            .thenApply((v) -> {
                try {
                    return userMapper.mapToProfileDto(
                        userFuture.get(),
                        followingFuture.get(),
                        followersFuture.get(),
                        tweetsFuture.get()
                    );
                } catch (ExecutionException | InterruptedException e) {
                    throw new UserException(e);
                }
            });
    }

    public CompletableFuture<UserDto> getUserSummary(UUID userId) {
        return CompletableFuture.supplyAsync(
            () -> userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + userId)),
            executorService
        ).thenApply(userMapper::mapToSummaryDto);
    }

    public CompletableFuture<UserDto> getUserSummaryByUserName(String userName) {
        return CompletableFuture.supplyAsync(
            () -> userRepository.findByUserName(userName)
                .orElseThrow(() -> new UserNotFoundException("User not found for name: " + userName)),
            executorService
        ).thenApply(userMapper::mapToSummaryDto);
    }

    public CompletableFuture<List<UserDto>> searchUser(String searchTerm) {
        return CompletableFuture.supplyAsync(
            () -> userRepository.searchUsers('%' + searchTerm + '%'),
            executorService
        ).thenApply(entities -> entities.stream().map(userMapper::mapToSummaryDto).collect(Collectors.toList()));
    }

    public CompletableFuture<Void> updateUser(UUID userId, UserUpdateRequest userUpdateRequest) {
        return CompletableFuture.supplyAsync(
                () -> {
                    if (userUpdateRequest.getEmail() != null && userRepository.existsByEmail(userUpdateRequest.getEmail())) {
                        throw new UserAlreadyExistsException("A user with this email already exists");
                    }

                    if (userUpdateRequest.getUserName() != null && userRepository.existsByUserName(userUpdateRequest.getUserName())) {
                        throw new UserAlreadyExistsException("A user with this username already exists");
                    }

                    UserEntity userEntity = userRepository.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException("User not found for id: " + userId));
                    userMapper.mapRequestToEntity(userUpdateRequest, userEntity);
                    return userEntity;
                },
                executorService
            )
            .thenAcceptAsync(userRepository::save, executorService);
    }

}
