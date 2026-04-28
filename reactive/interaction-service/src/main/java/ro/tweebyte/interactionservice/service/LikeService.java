package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.LikeableType;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final UserService userService;

    private final TweetService tweetService;

    private final LikeRepository likeRepository;

    private final ReplyRepository replyRepository;

    private final LikeMapper likeMapper;

    public Flux<LikeDto> getUserLikes(UUID userId) {
        return likeRepository.findByUserIdAndLikeableType(userId, LikeableType.TWEET.name())
            .flatMap(likeEntity -> tweetService.getTweetSummary(likeEntity.getLikeableId())
                .map(tweetSummary -> likeMapper.mapToDto(likeEntity, tweetSummary)));
    }

    public Flux<LikeDto> getTweetLikes(UUID tweetId) {
        return likeRepository.findByLikeableIdAndLikeableType(tweetId, LikeableType.TWEET.name())
            .flatMap(likeEntity -> userService.getUserSummary(likeEntity.getUserId())
                .map(userSummary -> likeMapper.mapToDto(likeEntity, userSummary)));
    }

    public Mono<Long> getTweetLikesCount(UUID tweetId) {
        return likeRepository.countByLikeableIdAndLikeableType(tweetId, LikeableType.TWEET.name());
    }

    public Mono<LikeDto> likeTweet(UUID userId, UUID tweetId) {
        return tweetService.getTweetSummary(tweetId)
            .flatMap(tweet -> {
                if (tweet == null) {
                    return Mono.error(new IllegalArgumentException("Tweet does not exist."));
                }
                return likeRepository.save(likeMapper.mapRequestToEntity(userId, tweetId, LikeableType.TWEET.name()))
                    .map(likeMapper::mapEntityToDto);
            });
    }

    public Mono<Void> unlikeTweet(UUID userId, UUID tweetId) {
        return likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, tweetId, LikeableType.TWEET.name());
    }

    public Mono<LikeDto> likeReply(UUID userId, UUID replyId) {
        return replyRepository.findById(replyId)
            .hasElement()
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.error(new IllegalArgumentException("Reply does not exist."));
                }
                return likeRepository.save(likeMapper.mapRequestToEntity(userId, replyId, LikeableType.REPLY.name()))
                    .map(likeMapper::mapEntityToDto);
            });
    }

    public Mono<Void> unlikeReply(UUID userId, UUID replyId) {
        return likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(userId, replyId, LikeableType.REPLY.name());
    }

}
