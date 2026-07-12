/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiFunction;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit-tests the eager tweet-update read: the parameterised owner-scoped SELECT, and the
 * in-memory collapse of the flat cartesian-product join rows back into one {@link TweetEntity}
 * with de-duplicated hashtag + mention lists. The {@code .map} row-mapper is captured and
 * driven with stub {@link Row}s so the {@code aggregate} reduction is exercised directly,
 * including the LEFT JOIN miss (NULL relation side) and the cartesian de-duplication.
 */
@ExtendWith(MockitoExtension.class)
class TweetRepositoryCustomImplTests {

	@Mock
	private DatabaseClient databaseClient;

	@Mock
	private DatabaseClient.GenericExecuteSpec executeSpec;

	@SuppressWarnings("rawtypes")
	@Mock
	private RowsFetchSpec rowsFetchSpec;

	@Mock
	private RowMetadata rowMetadata;

	private TweetRepositoryCustomImpl repository;

	private UUID tweetId;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.repository = new TweetRepositoryCustomImpl(this.databaseClient);
		this.tweetId = UUID.randomUUID();
		this.userId = UUID.randomUUID();
	}

	// Wire sql(...).bind(id).bind(userId).map(mapper).all() and return the captured row-mapper
	// applied to the supplied stub rows as the .all() Flux. This drives the real aggregate().
	@SuppressWarnings("unchecked")
	private void wireQueryReturning(Row... rows) {
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);
		given(this.executeSpec.bind(anyString(), any())).willReturn(this.executeSpec);
		ArgumentCaptor<BiFunction<Row, RowMetadata, Object>> mapperCaptor = ArgumentCaptor.forClass(BiFunction.class);
		given(this.executeSpec.map(mapperCaptor.capture())).willReturn(this.rowsFetchSpec);
		given(this.rowsFetchSpec.all()).willAnswer(invocation -> {
			BiFunction<Row, RowMetadata, Object> mapper = mapperCaptor.getValue();
			return Flux.fromArray(rows).map(row -> mapper.apply(row, this.rowMetadata));
		});
	}

	// A join row carrying the (constant) tweet columns plus one hashtag side and one mention
	// side. Null h_id / m_id models a LEFT JOIN miss for that relation.
	private Row joinRow(UUID hId, String hText, UUID mId, UUID mUserId, String mText) {
		Row row = org.mockito.Mockito.mock(Row.class);
		given(row.get("t_id", UUID.class)).willReturn(this.tweetId);
		given(row.get("t_user_id", UUID.class)).willReturn(this.userId);
		given(row.get("t_version", Long.class)).willReturn(3L);
		given(row.get("t_content", String.class)).willReturn("hello #spring @bob");
		LocalDateTime createdAt = LocalDateTime.of(2026, 6, 20, 12, 0);
		given(row.get("t_created_at", LocalDateTime.class)).willReturn(createdAt);
		given(row.get("t_media_ids", UUID[].class)).willReturn(new UUID[0]);
		given(row.get("h_id", UUID.class)).willReturn(hId);
		given(row.get("h_text", String.class)).willReturn(hText);
		given(row.get("m_id", UUID.class)).willReturn(mId);
		given(row.get("m_user_id", UUID.class)).willReturn(mUserId);
		given(row.get("m_text", String.class)).willReturn(mText);
		given(row.get("m_tweet_id", UUID.class)).willReturn((mId != null) ? this.tweetId : null);
		return row;
	}

	@Test
	void findWithRelations_bindsOwnerScopedSelect() {
		wireQueryReturning(joinRow(null, null, null, null, null));

		this.repository.findWithRelationsByIdAndUserId(this.tweetId, this.userId).block();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).contains("FROM tweets t").contains("LEFT JOIN tweet_hashtag")
			.contains("LEFT JOIN hashtags").contains("LEFT JOIN mentions")
			.contains("WHERE t.id = :id AND t.user_id = :userId");
		verify(this.executeSpec).bind("id", this.tweetId);
		verify(this.executeSpec).bind("userId", this.userId);
	}

	@Test
	void findWithRelations_noRows_returnsEmpty() {
		wireQueryReturning();

		StepVerifier.create(this.repository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.verifyComplete();
	}

	@Test
	void findWithRelations_leftJoinMiss_yieldsTweetWithEmptyRelations() {
		// A tweet with no hashtags and no mentions: the single join row has null h_id and m_id.
		wireQueryReturning(joinRow(null, null, null, null, null));

		StepVerifier.create(this.repository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.assertNext(result -> {
				TweetEntity tweet = result.tweet();
				assertThat(tweet.getId()).isEqualTo(this.tweetId);
				assertThat(tweet.getUserId()).isEqualTo(this.userId);
				assertThat(tweet.getVersion()).isEqualTo(3L);
				assertThat(tweet.getContent()).isEqualTo("hello #spring @bob");
				assertThat(result.hashtags()).isEmpty();
				assertThat(result.mentions()).isEmpty();
			})
			.verifyComplete();
	}

	@Test
	void findWithRelations_cartesianProduct_isDeduplicatedByRelationKey() {
		// Two hashtags x two mentions => 4 LEFT-JOIN rows. The collapse must yield exactly the
		// two distinct hashtags and two distinct mentions (keyed by h_id / m_id), not 4x dups.
		UUID hSpring = UUID.randomUUID();
		UUID hJava = UUID.randomUUID();
		UUID mBob = UUID.randomUUID();
		UUID mAlice = UUID.randomUUID();
		UUID bobUser = UUID.randomUUID();
		UUID aliceUser = UUID.randomUUID();

		wireQueryReturning(joinRow(hSpring, "spring", mBob, bobUser, "@bob"),
				joinRow(hSpring, "spring", mAlice, aliceUser, "@alice"),
				joinRow(hJava, "java", mBob, bobUser, "@bob"),
				joinRow(hJava, "java", mAlice, aliceUser, "@alice"));

		StepVerifier.create(this.repository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.assertNext(result -> {
				assertThat(result.hashtags()).extracting(HashtagEntity::getId)
					.containsExactly(hSpring, hJava);
				assertThat(result.hashtags()).extracting(HashtagEntity::getText)
					.containsExactly("spring", "java");
				assertThat(result.mentions()).extracting(MentionEntity::getId)
					.containsExactly(mBob, mAlice);
				assertThat(result.mentions()).extracting(MentionEntity::getText)
					.containsExactly("@bob", "@alice");
				assertThat(result.mentions()).extracting(MentionEntity::getUserId)
					.containsExactly(bobUser, aliceUser);
				assertThat(result.mentions()).allSatisfy(m -> assertThat(m.getTweetId()).isEqualTo(this.tweetId));
			})
			.verifyComplete();
	}

	@Test
	void findWithRelations_hashtagOnly_collectsHashtagAndNoMentions() {
		UUID hSpring = UUID.randomUUID();
		wireQueryReturning(joinRow(hSpring, "spring", null, null, null));

		StepVerifier.create(this.repository.findWithRelationsByIdAndUserId(this.tweetId, this.userId))
			.assertNext(result -> {
				assertThat(result.hashtags()).extracting(HashtagEntity::getText).containsExactly("spring");
				assertThat(result.mentions()).isEmpty();
			})
			.verifyComplete();
	}

}
