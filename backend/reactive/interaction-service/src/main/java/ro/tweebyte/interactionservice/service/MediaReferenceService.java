/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import ro.tweebyte.interactionservice.repository.ReplyRepository;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

@Service
@RequiredArgsConstructor
public class MediaReferenceService {

	private final ReplyRepository replyRepository;

	private final RetweetRepository retweetRepository;

	// Media still referenced by any reply or retweet. user-service's stale-media
	// GC unions this with its other sources to decide which media_assets rows are
	// orphaned; the caller collects into a Set, so the reply/retweet overlap here
	// need not be pre-deduplicated.
	public Flux<UUID> getReferencedMediaIds() {
		return Flux.concat(this.replyRepository.findReferencedMediaIds(),
				this.retweetRepository.findReferencedMediaIds());
	}

}
