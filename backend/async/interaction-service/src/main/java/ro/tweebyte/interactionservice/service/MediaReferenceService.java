/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

@Service
@RequiredArgsConstructor
public class MediaReferenceService {

	private final ReplyRepository replyRepository;

	private final RetweetRepository retweetRepository;

	private final ExecutorService executorService;

	// Media still referenced by any reply or retweet. user-service's stale-media
	// GC unions this with its other sources to decide which media_assets rows are
	// orphaned; the caller collects into a Set, so the reply/retweet overlap here
	// need not be pre-deduplicated.
	public CompletableFuture<List<UUID>> getReferencedMediaIds() {
		return CompletableFuture.supplyAsync(() -> {
			List<UUID> ids = new ArrayList<>(this.replyRepository.findReferencedMediaIds());
			ids.addAll(this.retweetRepository.findReferencedMediaIds());
			return ids;
		}, this.executorService);
	}

}
