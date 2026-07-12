/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionBoundaryContractTests {

	private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Test
	void replyCreateRequestRejectsMissingTweetId() {
		ReplyCreateRequest request = new ReplyCreateRequest().setContent("reply");

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage).contains("must not be null");
	}

	@Test
	void replyCreateRequestAllowsMissingUserIdBecauseControllerInjectsIt() {
		ReplyCreateRequest request = new ReplyCreateRequest().setTweetId(UUID.randomUUID()).setContent("reply");

		assertThat(violationsFor(request)).isEmpty();
	}

	@Test
	void replyCreateRequestRejectsContentLongerThan255Characters() {
		ReplyCreateRequest request = new ReplyCreateRequest().setTweetId(UUID.randomUUID()).setContent("x".repeat(256));

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("size must be between 0 and 255");
	}

	@Test
	void replyCreateRequestAcceptsContentAt255Characters() {
		ReplyCreateRequest request = new ReplyCreateRequest().setTweetId(UUID.randomUUID()).setContent("x".repeat(255));

		assertThat(violationsFor(request)).isEmpty();
	}

	@Test
	void replyCreateRequestChainAccessorsReturnSameInstance() {
		ReplyCreateRequest request = new ReplyCreateRequest();

		ReplyCreateRequest returned = request.setTweetId(UUID.randomUUID())
			.setUserId(UUID.randomUUID())
			.setContent("reply")
			.setMediaIds(new UUID[] { UUID.randomUUID() });

		assertThat(returned).isSameAs(request);
	}

	@Test
	void replyCreateRequestJsonUsesSnakeCaseIds() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID mediaId = UUID.randomUUID();
		ReplyCreateRequest request = new ReplyCreateRequest(tweetId, userId, "reply", new UUID[] { mediaId });

		String json = MAPPER.writeValueAsString(request);

		assertThat(json).contains("\"tweet_id\":\"" + tweetId + "\"", "\"user_id\":\"" + userId + "\"",
				"\"media_ids\":[\"" + mediaId + "\"]");
		assertThat(json).doesNotContain("tweetId", "userId", "mediaIds");
	}

	@Test
	void replyCreateRequestIgnoresUnknownFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();

		ReplyCreateRequest request = MAPPER.readValue("{\"tweet_id\":\"" + tweetId + "\",\"ignored\":true}",
				ReplyCreateRequest.class);

		assertThat(request.getTweetId()).isEqualTo(tweetId);
	}

	@Test
	void replyCreateRequestOmitsNullInjectedFields() throws JsonProcessingException {
		ReplyCreateRequest request = new ReplyCreateRequest().setTweetId(UUID.randomUUID()).setContent("reply");

		String json = MAPPER.writeValueAsString(request);

		assertThat(json).doesNotContain("user_id", "media_ids");
	}

	@Test
	void retweetCreateRequestRejectsMissingOriginalTweetId() {
		RetweetCreateRequest request = new RetweetCreateRequest().setContent("retweet");

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage).contains("must not be null");
	}

	@Test
	void retweetCreateRequestAllowsMissingRetweeterIdBecauseControllerInjectsIt() {
		RetweetCreateRequest request = new RetweetCreateRequest().setOriginalTweetId(UUID.randomUUID())
			.setContent("retweet");

		assertThat(violationsFor(request)).isEmpty();
	}

	@Test
	void retweetCreateRequestRejectsContentLongerThan255Characters() {
		RetweetCreateRequest request = new RetweetCreateRequest().setOriginalTweetId(UUID.randomUUID())
			.setContent("x".repeat(256));

		assertThat(violationsFor(request)).extracting(ConstraintViolation::getMessage)
			.contains("size must be between 0 and 255");
	}

	@Test
	void retweetCreateRequestJsonUsesSnakeCaseIds() throws JsonProcessingException {
		UUID originalTweetId = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();
		UUID mediaId = UUID.randomUUID();
		RetweetCreateRequest request = new RetweetCreateRequest(originalTweetId, retweeterId, "retweet",
				new UUID[] { mediaId });

		String json = MAPPER.writeValueAsString(request);

		assertThat(json).contains("\"original_tweet_id\":\"" + originalTweetId + "\"",
				"\"retweeter_id\":\"" + retweeterId + "\"", "\"media_ids\":[\"" + mediaId + "\"]");
		assertThat(json).doesNotContain("originalTweetId", "retweeterId", "mediaIds");
	}

	@Test
	void retweetCreateRequestChainAccessorsReturnSameInstance() {
		RetweetCreateRequest request = new RetweetCreateRequest();

		RetweetCreateRequest returned = request.setOriginalTweetId(UUID.randomUUID())
			.setRetweeterId(UUID.randomUUID())
			.setContent("retweet")
			.setMediaIds(new UUID[] { UUID.randomUUID() });

		assertThat(returned).isSameAs(request);
	}

	@Test
	void retweetCreateRequestIgnoresUnknownFields() throws JsonProcessingException {
		UUID originalTweetId = UUID.randomUUID();

		RetweetCreateRequest request = MAPPER.readValue(
				"{\"original_tweet_id\":\"" + originalTweetId + "\",\"ignored\":true}", RetweetCreateRequest.class);

		assertThat(request.getOriginalTweetId()).isEqualTo(originalTweetId);
	}

	@Test
	void replyUpdateRequestJsonUsesUserIdSnakeCase() throws JsonProcessingException {
		UUID userId = UUID.randomUUID();
		ReplyUpdateRequest request = new ReplyUpdateRequest(UUID.randomUUID(), userId, "updated");

		String json = MAPPER.writeValueAsString(request);

		assertThat(json).contains("\"user_id\":\"" + userId + "\"");
		assertThat(json).doesNotContain("userId");
	}

	@Test
	void replyUpdateRequestChainAccessorsReturnSameInstance() {
		ReplyUpdateRequest request = new ReplyUpdateRequest();

		ReplyUpdateRequest returned = request.setId(UUID.randomUUID()).setUserId(UUID.randomUUID()).setContent("body");

		assertThat(returned).isSameAs(request);
	}

	@Test
	void retweetUpdateRequestAllArgsConstructorPreservesState() {
		UUID id = UUID.randomUUID();
		UUID retweeterId = UUID.randomUUID();

		RetweetUpdateRequest request = new RetweetUpdateRequest(id, retweeterId, "updated");

		assertThat(request.getId()).isEqualTo(id);
		assertThat(request.getRetweeterId()).isEqualTo(retweeterId);
		assertThat(request.getContent()).isEqualTo("updated");
	}

	@Test
	void retweetUpdateRequestChainAccessorsReturnSameInstance() {
		RetweetUpdateRequest request = new RetweetUpdateRequest();

		RetweetUpdateRequest returned = request.setId(UUID.randomUUID())
			.setRetweeterId(UUID.randomUUID())
			.setContent("updated");

		assertThat(returned).isSameAs(request);
	}

	@Test
	void followCountsChainAccessorsReturnSameInstance() {
		FollowCountsDto counts = new FollowCountsDto();

		FollowCountsDto returned = counts.setFollowers(8L).setFollowing(13L);

		assertThat(returned).isSameAs(counts);
		assertThat(counts.getFollowers()).isEqualTo(8L);
		assertThat(counts.getFollowing()).isEqualTo(13L);
	}

	@Test
	void followCountsJsonRoundTripsCounts() throws JsonProcessingException {
		FollowCountsDto counts = new FollowCountsDto(8L, 13L);

		FollowCountsDto roundTripped = MAPPER.readValue(MAPPER.writeValueAsString(counts), FollowCountsDto.class);

		assertThat(roundTripped.getFollowers()).isEqualTo(8L);
		assertThat(roundTripped.getFollowing()).isEqualTo(13L);
	}

	@Test
	void profileInteractionsJsonUsesSnakeCaseEnvelopeFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		ProfileInteractionsDto dto = new ProfileInteractionsDto(new FollowCountsDto(1L, 2L),
				List.of(new TweetInteractionsEntryDto(tweetId, 3L, 4L, 5L, null)));

		String json = MAPPER.writeValueAsString(dto);

		assertThat(json).contains("\"follow_counts\"", "\"tweet_interactions\"", "\"tweet_id\":\"" + tweetId + "\"");
		assertThat(json).doesNotContain("followCounts", "tweetInteractions", "tweetId");
	}

	@Test
	void profileInteractionsChainAccessorsReturnSameInstance() {
		FollowCountsDto counts = new FollowCountsDto(1L, 2L);
		List<TweetInteractionsEntryDto> interactions = List.of();
		ProfileInteractionsDto dto = new ProfileInteractionsDto();

		ProfileInteractionsDto returned = dto.setFollowCounts(counts).setTweetInteractions(interactions);

		assertThat(returned).isSameAs(dto);
		assertThat(dto.getFollowCounts()).isSameAs(counts);
		assertThat(dto.getTweetInteractions()).isSameAs(interactions);
	}

	@Test
	void tweetInteractionsEntryJsonUsesSnakeCaseFields() throws JsonProcessingException {
		UUID tweetId = UUID.randomUUID();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 3L, 4L, 5L, null);

		String json = MAPPER.writeValueAsString(entry);

		assertThat(json).contains("\"tweet_id\":\"" + tweetId + "\"", "\"top_reply\":null");
		assertThat(json).doesNotContain("tweetId", "topReply");
	}

	@Test
	void tweetInteractionsEntryRecordAccessorsExposeCounts() {
		UUID tweetId = UUID.randomUUID();
		TweetInteractionsEntryDto entry = new TweetInteractionsEntryDto(tweetId, 3L, 4L, 5L, null);

		assertThat(entry.tweetId()).isEqualTo(tweetId);
		assertThat(entry.likes()).isEqualTo(3L);
		assertThat(entry.replies()).isEqualTo(4L);
		assertThat(entry.retweets()).isEqualTo(5L);
	}

	@Test
	void tweetInteractionsEntryEqualityUsesState() {
		UUID tweetId = UUID.randomUUID();

		TweetInteractionsEntryDto first = new TweetInteractionsEntryDto(tweetId, 3L, 4L, 5L, null);
		TweetInteractionsEntryDto second = new TweetInteractionsEntryDto(tweetId, 3L, 4L, 5L, null);

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void followingEntryJsonUsesSnakeCaseAndIsoDate() throws JsonProcessingException {
		UUID followedId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 5, 12, 30);
		FollowingEntryDto entry = new FollowingEntryDto(followedId, "alice", createdAt);

		String json = MAPPER.writeValueAsString(entry);

		assertThat(json).contains("\"followed_id\":\"" + followedId + "\"", "\"user_name\":\"alice\"",
				"\"created_at\":\"2026-07-05T12:30:00\"");
		assertThat(json).doesNotContain("followedId", "userName", "createdAt");
	}

	@Test
	void followingEntryIgnoresUnknownFields() throws JsonProcessingException {
		UUID followedId = UUID.randomUUID();

		FollowingEntryDto entry = MAPPER.readValue(
				"{\"followed_id\":\"" + followedId + "\",\"user_name\":\"alice\",\"ignored\":true}",
				FollowingEntryDto.class);

		assertThat(entry.followedId()).isEqualTo(followedId);
		assertThat(entry.userName()).isEqualTo("alice");
	}

	@Test
	void followingEntryOmitsNullCreatedAt() throws JsonProcessingException {
		FollowingEntryDto entry = new FollowingEntryDto(UUID.randomUUID(), "alice", null);

		assertThat(MAPPER.writeValueAsString(entry)).doesNotContain("created_at");
	}

	@Test
	void tweetSummaryAllArgsConstructorPreservesLists() {
		UUID id = UUID.randomUUID();
		List<String> hashtags = List.of("java");
		List<String> mentions = List.of("alice");

		TweetSummaryDto summary = new TweetSummaryDto(id, hashtags, mentions);

		assertThat(summary.getId()).isEqualTo(id);
		assertThat(summary.getHashtags()).isSameAs(hashtags);
		assertThat(summary.getMentions()).isSameAs(mentions);
	}

	@Test
	void tweetSummaryJsonUsesLists() throws JsonProcessingException {
		TweetSummaryDto summary = new TweetSummaryDto(UUID.randomUUID(), List.of("java"), List.of("alice"));

		String json = MAPPER.writeValueAsString(summary);

		assertThat(json).contains("\"hashtags\":[\"java\"]", "\"mentions\":[\"alice\"]");
	}

	@Test
	void tweetSummaryOmitsNullLists() throws JsonProcessingException {
		TweetSummaryDto summary = new TweetSummaryDto(UUID.randomUUID(), null, null);

		assertThat(MAPPER.writeValueAsString(summary)).doesNotContain("hashtags", "mentions");
	}

	@Test
	void tweetCountRecordAccessorsExposeAggregate() {
		UUID tweetId = UUID.randomUUID();

		TweetCount count = new TweetCount(tweetId, 42L);

		assertThat(count.tweetId()).isEqualTo(tweetId);
		assertThat(count.total()).isEqualTo(42L);
	}

	@Test
	void topReplyRecordAccessorsExposeProjection() {
		UUID tweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 5, 12, 30);

		TopReply reply = new TopReply(tweetId, replyId, userId, "reply", createdAt, 9L);

		assertThat(reply.tweetId()).isEqualTo(tweetId);
		assertThat(reply.id()).isEqualTo(replyId);
		assertThat(reply.userId()).isEqualTo(userId);
		assertThat(reply.content()).isEqualTo("reply");
		assertThat(reply.createdAt()).isEqualTo(createdAt);
		assertThat(reply.likeCount()).isEqualTo(9L);
	}

	@Test
	void topReplyEqualityUsesState() {
		UUID tweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 7, 5, 12, 30);

		TopReply first = new TopReply(tweetId, replyId, userId, "reply", createdAt, 9L);
		TopReply second = new TopReply(tweetId, replyId, userId, "reply", createdAt, 9L);

		assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
	}

	@Test
	void tweetInteractionRowProjectionCanExposeNullableTopReplyFields() {
		UUID tweetId = UUID.randomUUID();
		TweetInteractionRow row = new TweetInteractionRowStub(tweetId, 1L, 2L, 3L, null, null, null, null, null);

		assertThat(row.getTweetId()).isEqualTo(tweetId);
		assertThat(row.getLikeCount()).isEqualTo(1L);
		assertThat(row.getReplyCount()).isEqualTo(2L);
		assertThat(row.getRetweetCount()).isEqualTo(3L);
		assertThat(row.getTopReplyId()).isNull();
		assertThat(row.getTopReplyContent()).isNull();
	}

	private static <T> Set<ConstraintViolation<T>> violationsFor(T request) {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			return factory.getValidator().validate(request);
		}
	}

	private record TweetInteractionRowStub(UUID tweetId, Long likeCount, Long replyCount, Long retweetCount,
			UUID topReplyId, UUID topReplyUserId, String topReplyContent, LocalDateTime topReplyCreatedAt,
			Long topReplyLikeCount) implements TweetInteractionRow {

		@Override
		public UUID getTweetId() {
			return this.tweetId;
		}

		@Override
		public Long getLikeCount() {
			return this.likeCount;
		}

		@Override
		public Long getReplyCount() {
			return this.replyCount;
		}

		@Override
		public Long getRetweetCount() {
			return this.retweetCount;
		}

		@Override
		public UUID getTopReplyId() {
			return this.topReplyId;
		}

		@Override
		public UUID getTopReplyUserId() {
			return this.topReplyUserId;
		}

		@Override
		public String getTopReplyContent() {
			return this.topReplyContent;
		}

		@Override
		public LocalDateTime getTopReplyCreatedAt() {
			return this.topReplyCreatedAt;
		}

		@Override
		public Long getTopReplyLikeCount() {
			return this.topReplyLikeCount;
		}

	}

}
