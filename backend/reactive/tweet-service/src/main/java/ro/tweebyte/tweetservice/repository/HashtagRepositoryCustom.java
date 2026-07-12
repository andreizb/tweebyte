/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.repository;

import java.util.Collection;

import reactor.core.publisher.Flux;

import ro.tweebyte.tweetservice.entity.HashtagEntity;

/**
 * Custom fragment batching the insert of hashtag rows missing from the catalogue during a
 * tweet-update reconcile. Replaces the former per-text {@code save} (one INSERT per new
 * hashtag) with a single multi-row INSERT.
 *
 * @author Andrei Zbarcea
 */
public interface HashtagRepositoryCustom {

	/**
	 * Insert every hashtag in {@code hashtags} (each carrying a client-generated id +
	 * text) in one multi-row {@code INSERT INTO hashtags (id, text) VALUES (..),(..)} and
	 * echo the inserted entities back. No-op (empty {@link Flux}) when {@code hashtags} is
	 * empty.
	 * @param hashtags the hashtag entities to insert, each with id and text set
	 * @return the inserted hashtag entities
	 */
	Flux<HashtagEntity> insertAll(Collection<HashtagEntity> hashtags);

}
