package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReplyService {

    private final TweetService tweetService;

    private final UserService userService;

    private final ReplyRepository replyRepository;

    private final LikeRepository likeRepository;

    private final ReplyMapper replyMapper;

    public Mono<ReplyDto> createReply(ReplyCreateRequest request) {
        return tweetService.getTweetSummary(request.getTweetId())
            .flatMap(tweet -> {
                ReplyEntity reply = replyMapper.mapRequestToEntity(request);
                return replyRepository.save(reply);
            })
            .map(replyMapper::mapEntityToCreationDto);
    }

    public Mono<Void> updateReply(ReplyUpdateRequest request) {
        return replyRepository.findById(request.getId())
            .flatMap(reply -> {
                if (reply.getUserId().equals(request.getUserId())) {
                    replyMapper.mapRequestToEntity(request, reply);
                    return replyRepository.save(reply).then();
                }
                return Mono.error(new IllegalArgumentException("Unauthorized or reply not found"));
            });
    }

    public Mono<Void> deleteReply(UUID userId, UUID replyId) {
        return replyRepository.findById(replyId)
            .flatMap(reply -> {
                if (reply.getUserId().equals(userId)) {
                    return replyRepository.deleteById(replyId);
                }
                return Mono.error(new IllegalArgumentException("Unauthorized or reply not found"));
            });
    }

    public Flux<ReplyDto> getRepliesForTweet(UUID tweetId) {
        return replyRepository.findByTweetIdOrderByCreatedAtDesc(tweetId)
            .flatMap(replyEntity -> userService.getUserSummary(replyEntity.getUserId())
                .map(userSummary -> replyMapper.mapEntityToDto(replyEntity, userSummary.getUserName())));
    }

    public Mono<Long> getReplyCountForTweet(UUID tweetId) {
        return replyRepository.countByTweetId(tweetId);
    }

    public Mono<ReplyDto> getTopReplyForTweet(UUID tweetId) {
        return replyRepository.findTopReplyByLikesForTweetId(tweetId)
            .next()
            .flatMap(reply -> userService.getUserSummary(reply.getUserId())
                .map(userDto -> replyMapper.mapEntityToDto(reply, userDto.getUserName()))
            ).switchIfEmpty(Mono.just(new ReplyDto()));
    }

}
