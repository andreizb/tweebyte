/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import ro.tweebyte.interactionservice.service.MediaReferenceService;

@RestController
@RequestMapping(path = "/media")
@RequiredArgsConstructor
public class MediaReferenceController {

	private final MediaReferenceService mediaReferenceService;

	@GetMapping("/referenced")
	public Flux<UUID> getReferencedMediaIds() {
		return this.mediaReferenceService.getReferencedMediaIds();
	}

}
