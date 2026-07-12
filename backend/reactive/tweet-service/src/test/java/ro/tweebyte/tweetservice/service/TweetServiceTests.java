/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.ReplyDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetDto;
import ro.tweebyte.tweetservice.model.TweetHashtag;
import ro.tweebyte.tweetservice.model.TweetInteractionsDto;
import ro.tweebyte.tweetservice.model.TweetRelation;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.model.UserDto;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;
import ro.tweebyte.tweetservice.repository.TweetWithRelations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TweetServiceTests {

	@InjectMocks
	private TweetService tweetService;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private TweetMapper tweetMapper;

	@Mock
	private UserService userService;

	@Mock
	private MentionService mentionService;

	@Mock
	private HashtagService hashtagService;

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private MentionRepository mentionRepository;

	@Mock
	private TweetHashtagRepository tweetHashtagRepository;

	@Mock
	private ReactiveRedisTemplate<String, Object> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, Object> reactiveValueOperations;

	@Mock
	private org.springframework.beans.factory.ObjectProvider<reactor.util.retry.Retry> tweetTokensRetry;

	@Mock
	private TransactionalOperator txOperator;

	private UUID tweetId;

	private UUID userId;

	private TweetEntity tweetEntity;

	private TweetDto tweetDto;

	@BeforeEach
	void setup() {
		this.tweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();

		this.tweetEntity = new TweetEntity();
		this.tweetEntity.setId(this.tweetId);
		this.tweetEntity.setUserId(this.userId);
		this.tweetEntity.setContent("Sample Tweet Content");
		this.tweetEntity.setCreatedAt(LocalDateTime.now());

		this.tweetDto = new TweetDto();
		this.tweetDto.setId(this.tweetId);
		this.tweetDto.setContent("Sample Tweet Content");
		this.tweetDto.setLikesCount(10L);
		this.tweetDto.setRepliesCount(5L);
		this.tweetDto.setRetweetsCount(3L);

		// updateTweet wraps only the load + save + reconcile Mono with the operator; stub
		// it as identity so the test observes the unwrapped reactive behavior.
		given(this.txOperator.transactional(any(Mono.class))).willAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void getUserFeed_Success() {
		UUID followedUserId = UUID.randomUUID();

		MentionEntity mention = new MentionEntity();
		mention.setId(UUID.randomUUID());
		mention.setUserId(followedUserId);
		mention.setTweetId(this.tweetId);

		ReplyDto reply = new ReplyDto();
		reply.setId(UUID.randomUUID());
		reply.setContent("Sample Reply");
		TweetHashtag tweetHashtag = new TweetHashtag(this.tweetId, UUID.randomUUID(), "#example");

		given(this.interactionClient.getFollowedIds(this.userId)).willReturn(Flux.just(followedUserId));
		given(this.tweetRepository.findByUserIdIn(eq(List.of(followedUserId)), any(Pageable.class)))
			.willReturn(Flux.just(this.tweetEntity));
		// Page enrichment now fans out one consolidated interactions call carrying the same
		// per-tweet like/reply/retweet counts and top reply the four batch calls did.
		given(this.interactionClient.getTweetInteractions(anyList()))
			.willReturn(Mono.just(Map.of(this.tweetId, new TweetInteractionsDto(10L, 5L, 3L, reply))));
		// Reactive now mirrors async here too: one combined relation query (a UNION over the
		// hashtag and mention tables), split back per family by its kind discriminator.
		given(this.hashtagRepository.findRelationsByTweetIdIn(anyList())).willReturn(Flux.just(
				new TweetRelation(this.tweetId, "H", tweetHashtag.id(), null, tweetHashtag.text()),
				new TweetRelation(this.tweetId, "M", mention.getId(), mention.getUserId(), mention.getText())));

		given(this.tweetMapper.mapEntityToDto(any(), any(), any(), any(), any(ReplyDto.class), any(), any()))
			.willReturn(this.tweetDto);

		StepVerifier.create(this.tweetService.getUserFeed(this.userId, 0, 10))
			.expectNext(this.tweetDto)
			.verifyComplete();

		verify(this.interactionClient).getFollowedIds(this.userId);
		verify(this.interactionClient).getTweetInteractions(anyList());
		verify(this.tweetRepository).findByUserIdIn(eq(List.of(followedUserId)), any(Pageable.class));
		verify(this.hashtagRepository).findRelationsByTweetIdIn(anyList());
	}

	@Test
	void getUserFeed_Fallback() {
		String cacheKey = "followed_cache::" + this.userId;
		List<UUID> followedIds = List.of(UUID.randomUUID());

		MentionEntity mention = new MentionEntity();
		mention.setId(UUID.randomUUID());
		mention.setUserId(followedIds.getFirst());
		mention.setTweetId(this.tweetId);

		given(this.redisTemplate.opsForValue()).willReturn(this.reactiveValueOperations);
		// The Jackson cache serializer hands back the already-deserialized list, mirroring
		// what InteractionClient stored — not a raw JSON String.
		given(this.reactiveValueOperations.get(cacheKey))
			.willReturn(Mono.just(List.of(followedIds.get(0).toString())));

		given(this.tweetRepository.findByUserIdIn(any(), any(Pageable.class))).willReturn(Flux.just(this.tweetEntity));
		given(this.interactionClient.getTweetInteractions(anyList()))
			.willReturn(Mono.just(Map.of(this.tweetId, new TweetInteractionsDto(10L, 5L, 3L, new ReplyDto()))));
		TweetHashtag tweetHashtag = new TweetHashtag(this.tweetId, UUID.randomUUID(), "#example");
		given(this.hashtagRepository.findRelationsByTweetIdIn(anyList())).willReturn(Flux.just(
				new TweetRelation(this.tweetId, "H", tweetHashtag.id(), null, tweetHashtag.text()),
				new TweetRelation(this.tweetId, "M", mention.getId(), mention.getUserId(), mention.getText())));

		given(this.tweetMapper.mapEntityToDto(any(), any(), any(), any(), any(ReplyDto.class), any(), any()))
			.willReturn(this.tweetDto);

		StepVerifier.create(this.tweetService.getUserFeedWithCachedFollowed(this.userId, 0, 10, new Exception()))
			.expectNext(this.tweetDto)
			.verifyComplete();

		verify(this.reactiveValueOperations).get(cacheKey);
		verify(this.tweetRepository).findByUserIdIn(any(), any(Pageable.class));
		verify(this.hashtagRepository).findRelationsByTweetIdIn(anyList());
	}

	@Test
	void getUserFeed_OnErrorResumeFallsBackToCache() {
		// Layer-1 fallback: when getFollowedIds fails, getUserFeed's inline
		// .onErrorResume must recover the followed ids from the Redis cache
		// (mirrors async getUserFeed's .exceptionally path).
		UUID followedUserId = UUID.randomUUID();
		String cacheKey = "followed_cache::" + this.userId;

		MentionEntity mention = new MentionEntity();
		mention.setId(UUID.randomUUID());
		mention.setUserId(followedUserId);
		mention.setTweetId(this.tweetId);

		given(this.interactionClient.getFollowedIds(this.userId))
			.willReturn(Flux.error(new RuntimeException("Service error")));
		given(this.redisTemplate.opsForValue()).willReturn(this.reactiveValueOperations);
		given(this.reactiveValueOperations.get(cacheKey)).willReturn(Mono.just(List.of(followedUserId.toString())));

		given(this.tweetRepository.findByUserIdIn(eq(List.of(followedUserId)), any(Pageable.class)))
			.willReturn(Flux.just(this.tweetEntity));
		given(this.interactionClient.getTweetInteractions(anyList()))
			.willReturn(Mono.just(Map.of(this.tweetId, new TweetInteractionsDto(10L, 5L, 3L, new ReplyDto()))));
		TweetHashtag tweetHashtag = new TweetHashtag(this.tweetId, UUID.randomUUID(), "#example");
		given(this.hashtagRepository.findRelationsByTweetIdIn(anyList())).willReturn(Flux.just(
				new TweetRelation(this.tweetId, "H", tweetHashtag.id(), null, tweetHashtag.text()),
				new TweetRelation(this.tweetId, "M", mention.getId(), mention.getUserId(), mention.getText())));
		given(this.tweetMapper.mapEntityToDto(any(), any(), any(), any(), any(ReplyDto.class), any(), any()))
			.willReturn(this.tweetDto);

		StepVerifier.create(this.tweetService.getUserFeed(this.userId, 0, 10))
			.expectNext(this.tweetDto)
			.verifyComplete();

		verify(this.interactionClient).getFollowedIds(this.userId);
		verify(this.reactiveValueOperations).get(cacheKey);
		verify(this.tweetRepository).findByUserIdIn(eq(List.of(followedUserId)), any(Pageable.class));
		verify(this.hashtagRepository).findRelationsByTweetIdIn(anyList());
	}

	@Test
	void getTweet_Success() {
		UUID savedTweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();

		TweetEntity savedTweet = new TweetEntity();
		savedTweet.setId(savedTweetId);
		savedTweet.setUserId(this.userId);
		savedTweet.setContent("Sample Tweet Content");

		TweetDto expectedDto = new TweetDto();
		expectedDto.setId(savedTweetId);
		expectedDto.setContent("Sample Tweet Content");
		expectedDto.setLikesCount(10L);
		expectedDto.setRepliesCount(5L);
		expectedDto.setRetweetsCount(3L);

		ReplyDto replyDto = new ReplyDto();
		replyDto.setId(replyId);
		replyDto.setContent("Sample Reply");

		given(this.tweetRepository.findById(savedTweetId)).willReturn(Mono.just(savedTweet));
		given(this.interactionClient.getRepliesForTweet(savedTweetId)).willReturn(Flux.just(replyDto));
		given(this.interactionClient.getLikesCount(savedTweetId)).willReturn(Mono.just(10L));
		given(this.interactionClient.getRepliesCount(savedTweetId)).willReturn(Mono.just(5L));
		given(this.interactionClient.getRetweetsCount(savedTweetId)).willReturn(Mono.just(3L));

		given(this.hashtagRepository.findHashtagsByTweetId(savedTweetId)).willReturn(Flux.empty());
		given(this.mentionRepository.findMentionsByTweetId(savedTweetId)).willReturn(Flux.empty());

		given(this.tweetMapper.mapEntityToDto(savedTweet, 10L, 5L, 3L, List.of(replyDto), List.of(), List.of()))
			.willReturn(expectedDto);

		StepVerifier.create(this.tweetService.getTweet(savedTweetId)).expectNext(expectedDto).verifyComplete();

		verify(this.tweetRepository).findById(savedTweetId);
		verify(this.interactionClient).getRepliesForTweet(savedTweetId);
		verify(this.interactionClient).getLikesCount(savedTweetId);
		verify(this.interactionClient).getRepliesCount(savedTweetId);
		verify(this.interactionClient).getRetweetsCount(savedTweetId);
		verify(this.hashtagRepository).findHashtagsByTweetId(savedTweetId);
		verify(this.mentionRepository).findMentionsByTweetId(savedTweetId);
		verify(this.tweetMapper).mapEntityToDto(savedTweet, 10L, 5L, 3L, List.of(replyDto), List.of(), List.of());
	}

	@Test
	void getTweet_NotFound() {
		given(this.tweetRepository.findById(this.tweetId)).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.getTweet(this.tweetId))
			.expectErrorMatches(throwable -> throwable instanceof TweetNotFoundException
					&& throwable.getMessage().equals("Tweet not found for id: " + this.tweetId))
			.verify();
	}

	@Test
	void getUserTweetsSummary_Success() {
		given(this.tweetRepository.findByUserIdOrderByCreatedAtDescIdDesc(this.userId))
			.willReturn(Flux.just(this.tweetEntity));
		given(this.hashtagRepository.findHashtagsByTweetIdIn(anyList()))
			.willReturn(Flux.just(new TweetHashtag(this.tweetId, UUID.randomUUID(), "spring")));
		MentionEntity mentionEntity = new MentionEntity();
		mentionEntity.setTweetId(this.tweetId);
		mentionEntity.setText("alice");
		given(this.mentionRepository.findByTweetIdIn(anyList())).willReturn(Flux.just(mentionEntity));

		StepVerifier.create(this.tweetService.getUserTweetsSummary(this.userId))
			.expectNextMatches(
					summary -> summary.getId().equals(this.tweetId) && summary.getHashtags().equals(List.of("spring"))
							&& summary.getMentions().equals(List.of("alice")))
			.verifyComplete();
	}

	@Test
	void createTweet_Success() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(this.tweetId);
		request.setContent("New Tweet Content");

		given(this.tweetMapper.mapCreationRequestToEntity(request)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.mentionService.handleTweetCreationMentions(request)).willReturn(Mono.empty());
		given(this.hashtagService.handleTweetCreationHashtags(request)).willReturn(Mono.empty());
		given(this.tweetMapper.mapEntityToCreationDto(any())).willReturn(this.tweetDto);

		StepVerifier.create(this.tweetService.createTweet(request)).expectNext(this.tweetDto).verifyComplete();
	}

	@Test
	void updateTweet_Success() {
		// Diff-based update: ONE eager join read loads the tweet + its existing
		// hashtags/mentions, then save content/version and reconcile each
		// collection against the request tokens. "Updated Content" has no # or @
		// tokens and the tweet starts with no relations, so the diff is empty:
		// nothing removed, nothing added.
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("Updated Content");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of())));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetHashtagRepository.deleteLinks(any(), any())).willReturn(Mono.empty());
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();
		// No tokens, no pre-existing relations: the hashtag reconcile is a no-op prune
		// (deleteLinks with an empty stale list) and the mention reconcile is a no-op
		// replace (empty delete + empty insert).
		verify(this.tweetHashtagRepository).deleteLinks(eq(this.tweetId), eq(List.of()));
		verify(this.mentionRepository).replaceMentions(eq(List.of()), eq(List.of()));
	}

	@Test
	void updateTweet_WithHashtags_RebuildsLinks() {
		// Hashtag tokens not yet linked drive the find-or-create + link
		// addition. The tweet starts with no hashtag links, so nothing is
		// removed. No @ tokens → mention reconcile is a no-op.
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("benchmark new tweet 1 #new_1 #common_1");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of())));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());
		given(this.hashtagService.linkTweetToHashtagsCreatingMissing(eq(this.tweetId), any(), any()))
			.willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();

		// New links present → the find-or-create path runs and folds the (empty) stale-link
		// prune into the new-link insert; no separate deleteLinks is issued by the service.
		verify(this.hashtagService).linkTweetToHashtagsCreatingMissing(eq(this.tweetId), any(), eq(List.of()));
		verify(this.mentionRepository).replaceMentions(eq(List.of()), eq(List.of()));
	}

	@Test
	void updateTweet_NoHashtags_SkipsInsert() {
		// No hashtag tokens and no pre-existing links → the diff is empty:
		// no per-link delete and no insert. Mention path is a no-op (no @).
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("just a plain content update");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of())));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetHashtagRepository.deleteLinks(any(), any())).willReturn(Mono.empty());
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();

		verify(this.hashtagService, never()).linkTweetToHashtagsCreatingMissing(any(), any(), any());
		verify(this.tweetHashtagRepository).deleteLinks(eq(this.tweetId), eq(List.of()));
		verify(this.mentionRepository).replaceMentions(eq(List.of()), eq(List.of()));
	}

	@Test
	void updateTweet_WithMentions_RebuildsMentionRows() {
		// New @ tokens drive per-username resolve (userService.getUserIdLive), then the
		// two resolved mentions are inserted in ONE multi-row insertAll. The tweet
		// starts with no mentions, so nothing is removed.
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("hello @alice and @bob #common_1");

		UUID aliceId = UUID.randomUUID();
		UUID bobId = UUID.randomUUID();

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(new TweetWithRelations(this.tweetEntity, List.of(), List.of())));
		given(this.tweetMapper.mapUpdateRequestToEntity(request, this.tweetEntity)).willReturn(this.tweetEntity);
		given(this.tweetRepository.save(any())).willReturn(Mono.just(this.tweetEntity));
		given(this.mentionRepository.replaceMentions(anyList(), anyList())).willReturn(Mono.empty());
		given(this.hashtagService.linkTweetToHashtagsCreatingMissing(eq(this.tweetId), any(), any()))
			.willReturn(Mono.empty());
		given(this.userService.getUserIdLive("alice")).willReturn(Mono.just(aliceId));
		given(this.userService.getUserIdLive("bob")).willReturn(Mono.just(bobId));

		StepVerifier.create(this.tweetService.updateTweet(request)).verifyComplete();

		verify(this.userService).getUserIdLive("alice");
		verify(this.userService).getUserIdLive("bob");
		// Both resolved mentions inserted in a single statement, with no stale prune.
		ArgumentCaptor<List<MentionEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(this.mentionRepository).replaceMentions(eq(List.of()), captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue())
			.extracting(MentionEntity::getText)
			.containsExactlyInAnyOrder("alice", "bob");
	}

	@Test
	void updateTweet_NotFound_PropagatesException() {
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(this.tweetId);
		request.setUserId(this.userId);
		request.setContent("anything");

		given(this.tweetRepository.findWithRelationsByIdAndUserId(this.tweetId, this.userId)).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.updateTweet(request))
			.expectError(TweetNotFoundException.class)
			.verify();
	}

	@Test
	void deleteTweet_Success() {
		// Owner-scoped delete: findByIdAndUserId gates on ownership before the
		// mention/hashtag-link cleanup and the tweet delete fire.
		given(this.tweetRepository.findByIdAndUserId(this.tweetId, this.userId))
			.willReturn(Mono.just(this.tweetEntity));
		given(this.mentionRepository.deleteByTweetId(this.tweetId)).willReturn(Mono.empty());
		given(this.tweetHashtagRepository.deleteByTweetId(this.tweetId)).willReturn(Mono.empty());
		given(this.tweetRepository.deleteById(this.tweetId)).willReturn(Mono.empty());

		StepVerifier.create(this.tweetService.deleteTweet(this.userId, this.tweetId)).verifyComplete();
	}

	@Test
	void searchTweetsByHashtag_Success() {
		String hashtag = "#example";

		UserDto userDto = new UserDto();
		userDto.setId(this.userId);
		userDto.setUserName("SampleUser");

		given(this.tweetRepository.findByHashtag(eq(hashtag), anyInt(), anyInt()))
			.willReturn(Flux.just(this.tweetEntity));
		given(this.userService.getUserSummaries(List.of(this.userId))).willReturn(Mono.just(List.of(userDto)));
		given(this.tweetMapper.mapEntityToDto(this.tweetEntity, userDto)).willReturn(this.tweetDto);

		StepVerifier.create(this.tweetService.searchTweetsByHashtag(hashtag, 0, 10))
			.expectNext(this.tweetDto)
			.verifyComplete();
	}

	@Test
	void getTweetSummary_Success() {
		given(this.tweetRepository.findById(this.tweetId)).willReturn(Mono.just(this.tweetEntity));
		given(this.tweetMapper.mapEntityToDto(this.tweetEntity)).willReturn(this.tweetDto);

		StepVerifier.create(this.tweetService.getTweetSummary(this.tweetId)).expectNext(this.tweetDto).verifyComplete();
	}

}
