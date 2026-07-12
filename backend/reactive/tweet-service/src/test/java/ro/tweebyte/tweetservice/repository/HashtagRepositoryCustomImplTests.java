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

import ro.tweebyte.tweetservice.entity.HashtagEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit-tests the batched missing-hashtag insert: the multi-row SQL it renders, the
 * per-row id+text positional binds, the empty short-circuit, the client-generated id
 * invariant, and that the inserted rows are re-emitted on the returned {@link reactor.core.publisher.Flux}.
 */
@ExtendWith(MockitoExtension.class)
class HashtagRepositoryCustomImplTests {

	@Mock
	private DatabaseClient databaseClient;

	@Mock
	private DatabaseClient.GenericExecuteSpec executeSpec;

	private HashtagRepositoryCustomImpl repository;

	@BeforeEach
	void setUp() {
		this.repository = new HashtagRepositoryCustomImpl(this.databaseClient);
	}

	private void wireFluentSpec() {
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);
		given(this.executeSpec.bind(anyString(), any())).willReturn(this.executeSpec);
		given(this.executeSpec.then()).willReturn(Mono.empty());
	}

	private static HashtagEntity hashtag(String text) {
		return HashtagEntity.builder().id(UUID.randomUUID()).text(text).build();
	}

	@Test
	void insertAll_emptyCollection_shortCircuitsWithEmptyFlux() {
		StepVerifier.create(this.repository.insertAll(List.of())).verifyComplete();
		verifyNoInteractions(this.databaseClient);
	}

	@Test
	void insertAll_rendersMultiRowValuesBindsEachAndReemitsRows() {
		wireFluentSpec();
		HashtagEntity spring = hashtag("spring");
		HashtagEntity java = hashtag("java");

		StepVerifier.create(this.repository.insertAll(List.of(spring, java)))
			.expectNext(spring)
			.expectNext(java)
			.verifyComplete();

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(this.databaseClient).sql(sql.capture());
		assertThat(sql.getValue())
			.isEqualTo("INSERT INTO hashtags (id, text) VALUES (:id0, :text0), (:id1, :text1)");
		verify(this.executeSpec).bind("id0", spring.getId());
		verify(this.executeSpec).bind("text0", "spring");
		verify(this.executeSpec).bind("id1", java.getId());
		verify(this.executeSpec).bind("text1", "java");
	}

	@Test
	void insertAll_nullId_failsFastWithDocumentedInvariant() {
		HashtagEntity bad = HashtagEntity.builder().text("nohid").build();
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);

		assertThatThrownBy(() -> this.repository.insertAll(List.of(bad)).blockLast())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("hashtag id must be set before insert");
	}

	@Test
	void insertAll_nullText_failsFastWithDocumentedInvariant() {
		// id resolves first; text is requireNonNull-checked before any bind, so the row never
		// reaches the client.
		HashtagEntity bad = HashtagEntity.builder().id(UUID.randomUUID()).build();
		given(this.databaseClient.sql(anyString())).willReturn(this.executeSpec);

		assertThatThrownBy(() -> this.repository.insertAll(List.of(bad)).blockLast())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("hashtag text must be set before insert");
	}

}
