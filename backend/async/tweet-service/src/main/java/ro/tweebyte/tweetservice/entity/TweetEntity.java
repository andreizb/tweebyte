/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.entity;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tweets", indexes = { @Index(name = "idx_user_id", columnList = "user_id") })
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TweetEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Version
	private Long version;

	@Column(name = "content", nullable = false)
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "media_ids")
	private UUID[] mediaIds;

	@OneToMany(mappedBy = "tweetEntity", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<MentionEntity> mentions;

	// PERSIST/MERGE only — never REMOVE. Removing a tweet must delete its
	// tweet_hashtag join rows (Hibernate does this for the owning side regardless
	// of cascade) but NOT the shared HashtagEntity rows, which other tweets still
	// reference. CascadeType.ALL would cascade REMOVE to those shared rows, both
	// diverging from the reactive stack (which only deletes the join links) and
	// risking an FK violation when the hashtag is still linked elsewhere.
	@ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@JoinTable(name = "tweet_hashtag", joinColumns = { @JoinColumn(name = "tweet_id") },
			inverseJoinColumns = { @JoinColumn(name = "hashtag_id") })
	private Set<HashtagEntity> hashtags;

}
