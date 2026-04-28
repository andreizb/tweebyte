package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RetweetService {

    private final TweetService tweetService;

    private final RetweetRepository retweetRepository;

    private final RetweetMapper retweetMapper;

    private final UserService userService;

    public CompletableFuture<RetweetDto> createRetweet(RetweetCreateRequest request) {
        return CompletableFuture.supplyAsync(() -> tweetService.getTweetSummary(request.getOriginalTweetId()))
            .thenApply(tweet -> {
                if (tweet == null) {
                    throw new IllegalArgumentException("Tweet does not exist.");
                }
                RetweetEntity retweet = retweetMapper.mapRequestToEntity(request);
                return retweetRepository.save(retweet);
            })
            .thenApply(retweetMapper::mapEntityToDto);
    }

    public CompletableFuture<Void> updateRetweet(RetweetUpdateRequest request) {
        return CompletableFuture.runAsync(() -> {
            RetweetEntity retweet = retweetRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Retweet not found."));
            retweetMapper.mapRequestToEntity(request, retweet);
            retweetRepository.save(retweet);
        });
    }

    public CompletableFuture<Void> deleteRetweet(UUID retweetId, UUID userId) {
        return CompletableFuture.runAsync(() -> retweetRepository.deleteById(retweetId));
    }

    public CompletableFuture<List<RetweetDto>> getRetweetsByUser(UUID userId) {
        return CompletableFuture.supplyAsync(() ->
            retweetRepository.findByRetweeterId(userId)
        ).thenCompose(retweetEntities -> {
            List<CompletableFuture<RetweetDto>> retweetDtoFutures = retweetEntities.stream().map(retweetEntity -> {
                CompletableFuture<TweetDto> originalTweetFuture = CompletableFuture.supplyAsync(() ->
                    tweetService.getTweetSummary(retweetEntity.getOriginalTweetId()));

                return originalTweetFuture.thenCompose(tweetDto -> {
                    CompletableFuture<UserDto> userFuture = CompletableFuture.supplyAsync(() ->
                        userService.getUserSummary(tweetDto.getUserId()));

                    return userFuture.thenApply(userDto -> {
                        try {
                            return retweetMapper.mapEntityToDto(retweetEntity, userDto, tweetDto);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
            }).collect(Collectors.toList());

            return CompletableFuture.allOf(retweetDtoFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> retweetDtoFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));
        });
    }


    public CompletableFuture<List<RetweetDto>> getRetweetsOfTweet(UUID tweetId) {
        return CompletableFuture.supplyAsync(() ->
            retweetRepository.findByOriginalTweetId(tweetId)
        ).thenCompose(retweetEntities -> {
            List<CompletableFuture<RetweetDto>> futures = retweetEntities.stream().map(retweetEntity ->
                CompletableFuture.supplyAsync(() -> userService.getUserSummary(retweetEntity.getRetweeterId()))
                    .thenApply(userDto -> retweetMapper.mapEntityToDto(retweetEntity, userDto))
            ).collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));
        });
    }


    public CompletableFuture<Long> getRetweetCountOfTweet(UUID tweetId) {
        return CompletableFuture.supplyAsync(() -> retweetRepository.countByOriginalTweetId(tweetId));
    }

}
