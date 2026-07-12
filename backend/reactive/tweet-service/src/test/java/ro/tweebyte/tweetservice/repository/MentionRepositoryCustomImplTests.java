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

import ro.tweebyte.tweetservice.entity.MentionEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit-tests the batched {@code mentions} reconcile DML: the SQL each method renders, the
 * positional binds it issues (injection-safe), the empty-collection short-circuits, and the
 * single-CTE fold {@code replaceMentions} performs when both directions have work.
 */
@ExtendWith(MockitoExtension.class)
class MentionRepositoryCustomImplTests {

	@Mock
	private DatabaseClient databaseClient;

	@Mock
	private DatabaseClient.GenericExecuteSpec executeSpec;

	private MentionRepositoryCustomImpl repository;

	@BeforeEach
	void setUp() {
		this.repository = new MentionRepositoryCustomImpl(this.databaseClient);
	}

	// Make sql(...).bind(...).bind(...)... fluent: every stubbed spec returns itself, and
	// then() completes empty so StepVerifier can assert completion.
	private void wireFluentSpec() {
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);
		given(this.executeSpec.bind(anyString(), any())).willReturn(this.executeSpec);
		given(this.executeSpec.then()).willReturn(Mono.empty());
	}

	private static MentionEntity mention(UUID userId, String text, UUID tweetId) {
		return MentionEntity.builder().id(UUID.randomUUID()).userId(userId).text(text).tweetId(tweetId).build();
	}

	@Test
	void deleteByIdIn_emptyCollection_shortCircuitsWithoutTouchingClient() {
		StepVerifier.create(this.repository.deleteByIdIn(List.of())).verifyComplete();
		verifyNoInteractions(this.databaseClient);
	}

	@Test
	void deleteByIdIn_rendersInListWithOnePlaceholderPerIdAndBindsEach() {
		wireFluentSpec();
		UUID id0 = UUID.randomUUID();
		UUID id1 = UUID.randomUUID();

		StepVerifier.create(this.repository.deleteByIdIn(List.of(id0, id1))).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).isEqualTo("DELETE FROM mentions WHERE id IN (:id0, :id1)");
		verify(this.executeSpec).bind("id0", id0);
		verify(this.executeSpec).bind("id1", id1);
		verify(this.executeSpec).then();
	}

	@Test
	void insertAll_emptyCollection_shortCircuitsWithoutTouchingClient() {
		StepVerifier.create(this.repository.insertAll(List.of())).verifyComplete();
		verifyNoInteractions(this.databaseClient);
	}

	@Test
	void insertAll_rendersMultiRowValuesAndBindsEveryColumnPerRow() {
		wireFluentSpec();
		UUID userA = UUID.randomUUID();
		UUID userB = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		MentionEntity a = mention(userA, "@alice", tweetId);
		MentionEntity b = mention(userB, "@bob", tweetId);

		StepVerifier.create(this.repository.insertAll(List.of(a, b))).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).isEqualTo("INSERT INTO mentions (id, user_id, text, tweet_id) VALUES "
				+ "(:id0, :userId0, :text0, :tweetId0), (:id1, :userId1, :text1, :tweetId1)");
		// Every NOT NULL column is bound positionally for both rows.
		verify(this.executeSpec).bind("id0", a.getId());
		verify(this.executeSpec).bind("userId0", userA);
		verify(this.executeSpec).bind("text0", "@alice");
		verify(this.executeSpec).bind("tweetId0", tweetId);
		verify(this.executeSpec).bind("id1", b.getId());
		verify(this.executeSpec).bind("userId1", userB);
		verify(this.executeSpec).bind("text1", "@bob");
		verify(this.executeSpec).bind("tweetId1", tweetId);
	}

	@Test
	void insertAll_nullMentionId_failsFastWithDocumentedInvariant() {
		MentionEntity bad = MentionEntity.builder().userId(UUID.randomUUID()).text("@x").tweetId(UUID.randomUUID())
				.build();
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);

		assertThatThrownBy(() -> this.repository.insertAll(List.of(bad)).block())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("mention id must be set before insert");
	}

	@Test
	void replaceMentions_emptyInsert_degradesToDeleteByIdIn() {
		wireFluentSpec();
		UUID stale0 = UUID.randomUUID();

		StepVerifier.create(this.repository.replaceMentions(List.of(stale0), List.of())).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).isEqualTo("DELETE FROM mentions WHERE id IN (:id0)");
		verify(this.executeSpec).bind("id0", stale0);
	}

	@Test
	void replaceMentions_emptyStale_degradesToInsertAll() {
		wireFluentSpec();
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		MentionEntity row = mention(userId, "@carol", tweetId);

		StepVerifier.create(this.repository.replaceMentions(List.of(), List.of(row))).verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue()).startsWith("INSERT INTO mentions (id, user_id, text, tweet_id) VALUES");
		verify(this.executeSpec).bind("id0", row.getId());
	}

	@Test
	void replaceMentions_bothDirections_foldsDeleteAndInsertIntoOneCte() {
		wireFluentSpec();
		UUID stale0 = UUID.randomUUID();
		UUID stale1 = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID tweetId = UUID.randomUUID();
		MentionEntity fresh = mention(userId, "@dave", tweetId);

		StepVerifier.create(this.repository.replaceMentions(List.of(stale0, stale1), List.of(fresh)))
			.verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		// One data-modifying CTE: DELETE ... RETURNING folded with the multi-row INSERT.
		assertThat(sql.getValue())
			.isEqualTo("WITH del AS (DELETE FROM mentions WHERE id IN (:del0, :del1) RETURNING id) "
					+ "INSERT INTO mentions (id, user_id, text, tweet_id) VALUES "
					+ "(:id0, :userId0, :text0, :tweetId0)");
		verify(this.executeSpec).bind("del0", stale0);
		verify(this.executeSpec).bind("del1", stale1);
		verify(this.executeSpec).bind("id0", fresh.getId());
		verify(this.executeSpec).bind("userId0", userId);
		verify(this.executeSpec).bind("text0", "@dave");
		verify(this.executeSpec).bind("tweetId0", tweetId);
		// Single round-trip — only one statement is issued.
		verify(this.databaseClient).sql(anyString());
	}

	@Test
	void replaceMentions_bothEmpty_isANoOpInsertPath() {
		// Empty inserts short-circuit (via deleteByIdIn on the empty stale set) before any DML,
		// so the whole reconcile is a no-op completion with no SQL issued.
		StepVerifier.create(this.repository.replaceMentions(List.of(), List.of())).verifyComplete();
		verifyNoInteractions(this.databaseClient);
	}

}
