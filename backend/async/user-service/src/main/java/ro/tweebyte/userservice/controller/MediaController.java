/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ro.tweebyte.userservice.model.MediaAccessRequest;
import ro.tweebyte.userservice.service.MediaService;

@RestController
@RequestMapping(path = "/media")
@AllArgsConstructor
public class MediaController {

	private final MediaService mediaService;

	// Pure upload: store any file (image, text, …) as-is and return its id.
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CompletableFuture<ResponseEntity<Map<String, String>>> upload(@RequestPart("file") MultipartFile file) {
		return this.mediaService.upload(file);
	}

	// Derive a password-gated preview of original {id}.
	@PostMapping("/{id}/preview")
	public CompletableFuture<ResponseEntity<Map<String, String>>> preview(@PathVariable UUID id,
			@Valid @RequestBody MediaAccessRequest request) {
		return this.mediaService.preview(id, request.getPassword());
	}

	// Reveal the original behind preview {id} when the password matches its gate.
	@PostMapping("/{id}/reveal")
	public CompletableFuture<ResponseEntity<StreamingResponseBody>> reveal(@PathVariable UUID id,
			@Valid @RequestBody MediaAccessRequest request) {
		return this.mediaService.reveal(id, request.getPassword());
	}

	@GetMapping("/{id}")
	public CompletableFuture<ResponseEntity<StreamingResponseBody>> download(@PathVariable UUID id) {
		return this.mediaService.download(id);
	}

	@GetMapping("/{id}/exists")
	public CompletableFuture<ResponseEntity<Map<String, Boolean>>> exists(@PathVariable UUID id) {
		return this.mediaService.exists(id);
	}

	@DeleteMapping("/cache")
	public ResponseEntity<Void> flushCache() {
		this.mediaService.flushCache();
		return ResponseEntity.noContent().build();
	}

}
