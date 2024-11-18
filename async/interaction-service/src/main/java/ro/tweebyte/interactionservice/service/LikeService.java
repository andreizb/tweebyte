package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final UserService userService;

    private final TweetService tweetService;

    private final LikeRepository likeRepository;

    private final ReplyRepository replyRepository;

    private final LikeMapper likeMapper;

    public CompletableFuture<List<LikeDto>> getUserLikes(UUID userId) {
        return CompletableFuture.supplyAsync(() ->
            likeRepository.findByUserIdAndLikeableType(userId, LikeEntity.LikeableType.TWEET)
        ).thenCompose(likeEntities -> {
            List<CompletableFuture<LikeDto>> futures = likeEntities.stream().map(likeEntity ->
                CompletableFuture.supplyAsync(() -> tweetService.getTweetSummary(likeEntity.getLikeableId()))
                    .thenApply(tweetSummary -> likeMapper.mapToDto(likeEntity, tweetSummary))
            ).collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        });
    }


    public CompletableFuture<List<LikeDto>> getTweetLikes(UUID tweetId) {
        return CompletableFuture.supplyAsync(() ->
            likeRepository.findByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET)
        ).thenCompose(likeEntities -> {
            List<CompletableFuture<LikeDto>> futures = likeEntities.stream().map(likeEntity ->
                CompletableFuture.supplyAsync(() -> userService.getUserSummary(likeEntity.getUserId()))
                    .thenApply(userSummary -> likeMapper.mapToDto(likeEntity, userSummary))
            ).collect(Collectors.toList());

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        });
    }


    public CompletableFuture<Long> getTweetLikesCount(UUID tweetId) {
        return CompletableFuture.supplyAsync(() ->
            likeRepository.countByLikeableIdAndLikeableType(tweetId, LikeEntity.LikeableType.TWEET)
        );
    }

    public CompletableFuture<LikeDto> likeTweet(UUID userId, UUID tweetId) {
        return CompletableFuture.supplyAsync(() -> tweetService.getTweetSummary(tweetId))
            .thenApply(tweet -> {
                if (tweet == null) {
                    throw new IllegalArgumentException("Tweet does not exist.");
                }
                return likeRepository.save(likeMapper.mapRequestToEntity(userId, tweetId, LikeEntity.LikeableType.TWEET));
            })
            .thenApply(likeMapper::mapEntityToDto);
    }

    @Transactional
    @Async(value = "executorService")
    public CompletableFuture<Void> unlikeTweet(UUID userId, UUID tweetId) {
        likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, tweetId, LikeEntity.LikeableType.TWEET);
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<LikeDto> likeReply(UUID userId, UUID replyId) {
        return CompletableFuture.supplyAsync(() ->
                replyRepository.findByIdAndUserId(replyId, userId).isPresent()
            )
            .thenApply(exists -> {
                if (!exists) {
                    throw new IllegalArgumentException("Reply does not exist.");
                }
                return likeMapper.mapRequestToEntity(userId, replyId, LikeEntity.LikeableType.REPLY);
            })
            .thenApply(likeRepository::save)
            .thenApply(likeMapper::mapEntityToDto);
    }

    @Transactional
    @Async(value = "executorService")
    public CompletableFuture<Void> unlikeReply(UUID userId, UUID replyId) {
        likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, replyId, LikeEntity.LikeableType.REPLY);
        return CompletableFuture.completedFuture(null);
    }

}
