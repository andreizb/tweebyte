/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;

/**
 * {@link DatabaseClient}-backed implementation of the eager tweet update read. The
 * owner-scoped diff is ONE SELECT with two {@code LEFT JOIN}s (hashtags + mentions) whose
 * flat, cartesian-product row set is collapsed back in memory, de-duplicating each relation
 * by its primary key — the R2DBC stand-in for the async stack's {@code @EntityGraph} fetch.
 *
 * @author Andrei Zbarcea
 */
@RequiredArgsConstructor
public class TweetRepositoryCustomImpl implements TweetRepositoryCustom {

	// Tweet columns are prefixed (t_*) so they never collide with the relation columns and
	// so a NULL relation side (a tweet with no hashtags/mentions, the LEFT JOIN miss) is
	// distinguishable from the always-present tweet columns. h_id/m_id NULL ⇒ no row to add.
	private static final String SELECT_WITH_RELATIONS = """
			SELECT t.id AS t_id, t.user_id AS t_user_id, t.version AS t_version, t.content AS t_content, \
			t.created_at AS t_created_at, t.media_ids AS t_media_ids, \
			h.id AS h_id, h.text AS h_text, \
			m.id AS m_id, m.user_id AS m_user_id, m.text AS m_text, m.tweet_id AS m_tweet_id \
			FROM tweets t \
			LEFT JOIN tweet_hashtag th ON t.id = th.tweet_id \
			LEFT JOIN hashtags h ON th.hashtag_id = h.id \
			LEFT JOIN mentions m ON t.id = m.tweet_id \
			WHERE t.id = :id AND t.user_id = :userId""";

	private final DatabaseClient databaseClient;

	@Override
	public Mono<TweetWithRelations> findWithRelationsByIdAndUserId(UUID id, UUID userId) {
		return this.databaseClient.sql(SELECT_WITH_RELATIONS)
			.bind("id", id)
			.bind("userId", userId)
			.map((row, metadata) -> new FlatRow(
					row.get("t_id", UUID.class),
					row.get("t_user_id", UUID.class),
					row.get("t_version", Long.class),
					row.get("t_content", String.class),
					row.get("t_created_at", LocalDateTime.class),
					row.get("t_media_ids", UUID[].class),
					row.get("h_id", UUID.class),
					row.get("h_text", String.class),
					row.get("m_id", UUID.class),
					row.get("m_user_id", UUID.class),
					row.get("m_text", String.class),
					row.get("m_tweet_id", UUID.class)))
			.all()
			.collectList()
			.flatMap(rows -> rows.isEmpty() ? Mono.empty() : Mono.just(aggregate(rows)));
	}

	// Rebuild the aggregate from the flat join rows: the tweet is the same across every
	// row (built once from the first), hashtags keyed by h_id and mentions keyed by m_id
	// so the cartesian product of the two LEFT JOINs is de-duplicated back to one entity
	// each. LinkedHashMap keeps a stable, first-seen order.
	private static TweetWithRelations aggregate(List<FlatRow> rows) {
		TweetEntity tweet = mapTweet(rows.get(0));
		Map<UUID, HashtagEntity> hashtags = new LinkedHashMap<>();
		Map<UUID, MentionEntity> mentions = new LinkedHashMap<>();
		for (FlatRow row : rows) {
			if (row.hId() != null) {
				hashtags.computeIfAbsent(row.hId(), hid -> buildHashtag(hid, row.hText()));
			}
			if (row.mId() != null) {
				mentions.computeIfAbsent(row.mId(), mid -> buildMention(mid, row.mUserId(), row.mText(), row.mTweetId()));
			}
		}
		return new TweetWithRelations(tweet, new ArrayList<>(hashtags.values()), new ArrayList<>(mentions.values()));
	}

	private static TweetEntity mapTweet(FlatRow row) {
		return TweetEntity.builder()
			.id(row.tId())
			.userId(row.tUserId())
			.version(row.tVersion())
			.content(row.tContent())
			.createdAt(row.tCreatedAt())
			.mediaIds(row.tMediaIds())
			.build();
	}

	private static HashtagEntity buildHashtag(UUID id, String text) {
		return HashtagEntity.builder().id(id).text(text).build();
	}

	private static MentionEntity buildMention(UUID id, UUID userId, String text, UUID tweetId) {
		return MentionEntity.builder().id(id).userId(userId).text(text).tweetId(tweetId).build();
	}

	// One flat projection row of the cartesian-product result set. Every column is read
	// eagerly inside the DatabaseClient map callback — an R2DBC Row is valid only for the
	// duration of that callback, so the values must be lifted into this record before the
	// connection buffers are released; reading the Row afterwards yields freed garbage.
	private record FlatRow(UUID tId, UUID tUserId, Long tVersion, String tContent, LocalDateTime tCreatedAt,
			UUID[] tMediaIds, UUID hId, String hText, UUID mId, UUID mUserId, String mText, UUID mTweetId) {
	}

}
