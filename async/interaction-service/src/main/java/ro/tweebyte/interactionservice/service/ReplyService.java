package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReplyService {

    private final TweetService tweetService;

    private final UserService userService;

    private final ReplyRepository replyRepository;

    private final ReplyMapper replyMapper;

    public CompletableFuture<ReplyDto> createReply(ReplyCreateRequest request) {
        return CompletableFuture.supplyAsync(() -> tweetService.getTweetSummary(request.getTweetId()))
            .thenApply(tweet -> {
                if (tweet == null) {
                    throw new IllegalArgumentException("Tweet does not exist.");
                }
                ReplyEntity reply = replyMapper.mapRequestToEntity(request);
                return replyRepository.save(reply);
            })
            .thenApply(replyMapper::mapEntityToCreationDto);
    }

    public CompletableFuture<Void> updateReply(ReplyUpdateRequest request) {
        return CompletableFuture.runAsync(() -> {
            ReplyEntity reply = replyRepository.findById(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Reply not found."));
            if (reply.getUserId().equals(request.getUserId())) {
                replyMapper.mapRequestToEntity(request, reply);
                replyRepository.save(reply);
            }
        });
    }

    public CompletableFuture<Void> deleteReply(UUID userId, UUID replyId) {
        return CompletableFuture.supplyAsync(() -> replyRepository.findById(replyId))
                .thenAccept(replyEntity -> replyEntity.ifPresent(entity -> {
                    if (entity.getUserId().equals(userId)) {
                        replyRepository.deleteById(replyId);
                    }
                })
            );
    }

    public CompletableFuture<List<ReplyDto>> getRepliesForTweet(UUID tweetId) {
        return CompletableFuture.supplyAsync(() ->
            replyRepository.findByTweetIdOrderByCreatedAtDesc(tweetId)
        ).thenCompose(replyEntities -> {
            List<CompletableFuture<ReplyDto>> replyFutures = replyEntities.stream().map(replyEntity ->
                CompletableFuture.supplyAsync(() -> userService.getUserSummary(replyEntity.getUserId()))
                    .thenApply(userSummary -> replyMapper.mapEntityToDto(replyEntity, userSummary.getUserName()))
            ).toList();

            return CompletableFuture.allOf(replyFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> replyFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        });
    }

    public CompletableFuture<Long> getReplyCountForTweet(UUID tweetId) {
        return CompletableFuture.supplyAsync(() -> replyRepository.countByTweetId(tweetId));
    }

    public CompletableFuture<ReplyDto> getTopReplyForTweet(UUID tweetId) {
        return CompletableFuture.supplyAsync(() -> replyRepository.findTopReplyByLikesForTweetId(tweetId, PageRequest.of(0, 1)))
            .thenApply(page -> {
                if (!page.getContent().isEmpty()) {
                    ReplyDto replyDto = page.getContent().get(0);
                    UserDto userDto = userService.getUserSummary(page.getContent().get(0).getUserId());
                    replyDto.setUserName(userDto.getUserName());
                    return replyDto;
                }
                return new ReplyDto();
            });
    }

}
