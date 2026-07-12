/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit-tests the batched {@code tweet_hashtag} reconcile DML: the SQL each method renders,
 * the positional binds (the owner {@code tweetId} plus one placeholder per hashtag id), the
 * empty-list short-circuits, and the single-CTE fold {@code replaceLinks} performs when both
 * the prune and the add have work.
 */
@ExtendWith(MockitoExtension.class)
class TweetHashtagRepositoryCustomImplTests {

	@Mock
	private DatabaseClient databaseClient;

	@Mock
	private DatabaseClient.GenericExecuteSpec executeSpec;

	private TweetHashtagRepositoryCustomImpl repository;

	@BeforeEach
	void setUp() {
		this.repository = new TweetHashtagRepositoryCustomImpl(this.databaseClient);
	}

	private void wireFluentSpec() {
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);
		given(this.executeSpec.bind(anyString(), any())).willReturn(this.executeSpec);
		given(this.executeSpec.then()).willReturn(Mono.empty());
	}

	@Test
	void deleteLinks_emptyIds_shortCircuits() {
		StepVerifier.create(this.repository.deleteLinks(UUID.randomUUID(), List.of())).verifyComplete();
		verifyNoInteractions(this.databaseClient);
	}

	@Test
	void deleteLinks_scopesToTweetAndRendersHashtagInList() {
		wireFluentSpec();
		UUID tweetId = UUID.randomUUID();
		UUID h0 = UUID.randomUUID();
		UUID h1 = UUID.randomUUID();

		StepVerifier.create(this.repository.deleteLinks(tweetId, List.of(h0, h1))).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue())
			.isEqualTo("DELETE FROM tweet_hashtag WHERE tweet_id = :tweetId AND hashtag_id IN (:h0, :h1)");
		verify(this.executeSpec).bind("tweetId", tweetId);
		verify(this.executeSpec).bind("h0", h0);
		verify(this.executeSpec).bind("h1", h1);
	}

	@Test
	void insertLinks_emptyIds_shortCircuits() {
		StepVerifier.create(this.repository.insertLinks(UUID.randomUUID(), List.of())).verifyComplete();
		verifyNoInteractions(this.databaseClient);
	}

	@Test
	void insertLinks_rendersMultiRowValuesReusingTweetIdPlaceholder() {
		wireFluentSpec();
		UUID tweetId = UUID.randomUUID();
		UUID h0 = UUID.randomUUID();
		UUID h1 = UUID.randomUUID();

		StepVerifier.create(this.repository.insertLinks(tweetId, List.of(h0, h1))).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).isEqualTo("INSERT INTO tweet_hashtag (tweet_id, hashtag_id) VALUES "
				+ "(:tweetId, :h0), (:tweetId, :h1)");
		verify(this.executeSpec).bind("tweetId", tweetId);
		verify(this.executeSpec).bind("h0", h0);
		verify(this.executeSpec).bind("h1", h1);
	}

	@Test
	void replaceLinks_emptyNew_degradesToDeleteLinks() {
		wireFluentSpec();
		UUID tweetId = UUID.randomUUID();
		UUID stale0 = UUID.randomUUID();

		StepVerifier.create(this.repository.replaceLinks(tweetId, List.of(stale0), List.of())).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue())
			.isEqualTo("DELETE FROM tweet_hashtag WHERE tweet_id = :tweetId AND hashtag_id IN (:h0)");
		verify(this.executeSpec).bind("h0", stale0);
	}

	@Test
	void replaceLinks_emptyStale_degradesToInsertLinks() {
		wireFluentSpec();
		UUID tweetId = UUID.randomUUID();
		UUID fresh0 = UUID.randomUUID();

		StepVerifier.create(this.repository.replaceLinks(tweetId, List.of(), List.of(fresh0))).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).startsWith("INSERT INTO tweet_hashtag (tweet_id, hashtag_id) VALUES");
		verify(this.executeSpec).bind("h0", fresh0);
	}

	@Test
	void replaceLinks_bothDirections_foldsPruneAndAddIntoOneCte() {
		wireFluentSpec();
		UUID tweetId = UUID.randomUUID();
		UUID stale0 = UUID.randomUUID();
		UUID fresh0 = UUID.randomUUID();
		UUID fresh1 = UUID.randomUUID();

		StepVerifier.create(this.repository.replaceLinks(tweetId, List.of(stale0), List.of(fresh0, fresh1)))
			.verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue())
			.isEqualTo("WITH del AS (DELETE FROM tweet_hashtag WHERE tweet_id = :tweetId AND hashtag_id IN (:s0) "
					+ "RETURNING hashtag_id) INSERT INTO tweet_hashtag (tweet_id, hashtag_id) VALUES "
					+ "(:tweetId, :h0), (:tweetId, :h1)");
		verify(this.executeSpec).bind("tweetId", tweetId);
		verify(this.executeSpec).bind("s0", stale0);
		verify(this.executeSpec).bind("h0", fresh0);
		verify(this.executeSpec).bind("h1", fresh1);
		// Single round-trip.
		verify(this.databaseClient).sql(anyString());
	}

}
