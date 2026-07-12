/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.model;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TweetProjectionContractTests {

	@Test
	void tweetRelationCarriesMentionRowsWithMentionUser() {
		UUID tweetId = UUID.randomUUID();
		UUID mentionId = UUID.randomUUID();
		UUID mentionedUser = UUID.randomUUID();

		TweetRelation relation = new TweetRelation(tweetId, "M", mentionId, mentionedUser, "@alice");

		assertThat(relation.tweetId()).isEqualTo(tweetId);
		assertThat(relation.kind()).isEqualTo("M");
		assertThat(relation.id()).isEqualTo(mentionId);
		assertThat(relation.userId()).isEqualTo(mentionedUser);
		assertThat(relation.text()).isEqualTo("@alice");
	}

	@Test
	void tweetRelationAllowsHashtagRowsWithoutMentionUser() {
		UUID tweetId = UUID.randomUUID();
		UUID hashtagId = UUID.randomUUID();

		TweetRelation relation = new TweetRelation(tweetId, "H", hashtagId, null, "#java");

		assertThat(relation.tweetId()).isEqualTo(tweetId);
		assertThat(relation.kind()).isEqualTo("H");
		assertThat(relation.id()).isEqualTo(hashtagId);
		assertThat(relation.userId()).isNull();
		assertThat(relation.text()).isEqualTo("#java");
	}

	@Test
	void tweetHashtagCarriesJoinProjection() {
		UUID tweetId = UUID.randomUUID();
		UUID hashtagId = UUID.randomUUID();

		TweetHashtag hashtag = new TweetHashtag(tweetId, hashtagId, "webflux");

		assertThat(hashtag).isEqualTo(new TweetHashtag(tweetId, hashtagId, "webflux"));
		assertThat(hashtag.tweetId()).isEqualTo(tweetId);
		assertThat(hashtag.id()).isEqualTo(hashtagId);
		assertThat(hashtag.text()).isEqualTo("webflux");
	}

	@Test
	void tweetRelationRecordEqualityIncludesKindAndText() {
		UUID tweetId = UUID.randomUUID();
		UUID relationId = UUID.randomUUID();

		TweetRelation hashtag = new TweetRelation(tweetId, "H", relationId, null, "#java");
		TweetRelation mention = new TweetRelation(tweetId, "M", relationId, null, "@java");

		assertThat(hashtag).isNotEqualTo(mention);
		assertThat(hashtag).isEqualTo(new TweetRelation(tweetId, "H", relationId, null, "#java"));
	}

	@Test
	void tweetRequestInterfaceExposesSharedCreationAndUpdateFields() {
		UUID creationId = UUID.randomUUID();
		UUID updateId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		List<TweetRequest> requests = List.of(new TweetCreationRequest(creationId, userId, "create content", null),
				new TweetUpdateRequest(updateId, userId, "update content"));

		assertThat(requests).extracting(TweetRequest::getId).containsExactly(creationId, updateId);
		assertThat(requests).extracting(TweetRequest::getUserId).containsExactly(userId, userId);
		assertThat(requests).extracting(TweetRequest::getContent).containsExactly("create content", "update content");
	}

}
