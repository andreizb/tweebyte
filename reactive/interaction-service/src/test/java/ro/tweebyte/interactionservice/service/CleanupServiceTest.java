package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.exception.TweetNotFoundException;
import ro.tweebyte.interactionservice.model.LikeableType;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.repository.FollowRepository;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

	@Mock
	private TweetService tweetService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private RetweetRepository retweetRepository;

	private CleanupService cleanupService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		cleanupService = new CleanupService(tweetService, followRepository, likeRepository, replyRepository, retweetRepository);
	}

	@Test
	void cleanupRejectedFollowRequests_Success() {
		given(followRepository.findByStatus(Status.REJECTED.name()))
			.willReturn(Flux.just(new FollowEntity(UUID.randomUUID(), LocalDateTime.now(), true, UUID.randomUUID(), UUID.randomUUID(), "REJECTED")));
		given(followRepository.deleteAll(anyList()))
			.willReturn(Mono.empty());

		StepVerifier.create(cleanupService.cleanupRejectedFollowRequests())
			.verifyComplete();

		verify(followRepository, times(1)).findByStatus(Status.REJECTED.name());
		verify(followRepository, times(1)).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanLikes_Success() {
		UUID likeableId = UUID.randomUUID();

		given(likeRepository.findAll())
			.willReturn(Flux.just(new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true, likeableId, UUID.randomUUID(), "TWEET")));
		given(tweetService.getTweetSummary(any(UUID.class)))
			.willReturn(Mono.empty())
			.willReturn(Mono.error(new TweetNotFoundException("Tweet not found")));
		given(likeRepository.deleteAll(anyList()))
			.willReturn(Mono.empty());

		StepVerifier.create(cleanupService.cleanupOrphanLikes())
			.verifyComplete();

		verify(likeRepository, times(1)).findAll();
		verify(tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(likeRepository, times(1)).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanReplies_Success() {
		UUID tweetId = UUID.randomUUID();

		given(replyRepository.findAll())
			.willReturn(Flux.just(new ReplyEntity(UUID.randomUUID(), LocalDateTime.now(), tweetId, UUID.randomUUID(), "comment", true)));
		given(tweetService.getTweetSummary(any(UUID.class)))
			.willReturn(Mono.error(new TweetNotFoundException("Tweet not found")));
		given(replyRepository.deleteAll(anyList()))
			.willReturn(Mono.empty());

		StepVerifier.create(cleanupService.cleanupOrphanReplies())
			.verifyComplete();

		verify(replyRepository, times(1)).findAll();
		verify(tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(replyRepository, times(1)).deleteAll(anyList());
	}

	@Test
	void cleanupOrphanRetweets_Success() {
		UUID tweetId = UUID.randomUUID();

		given(retweetRepository.findAll())
			.willReturn(Flux.just(new RetweetEntity(UUID.randomUUID(), LocalDateTime.now(), tweetId, UUID.randomUUID(), "comment", true)));
		given(tweetService.getTweetSummary(any(UUID.class)))
			.willReturn(Mono.error(new TweetNotFoundException("Tweet not found")));
		given(retweetRepository.deleteAll(anyList()))
			.willReturn(Mono.empty());

		StepVerifier.create(cleanupService.cleanupOrphanRetweets())
			.verifyComplete();

		verify(retweetRepository, times(1)).findAll();
		verify(tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(retweetRepository, times(1)).deleteAll(anyList());
	}
}
