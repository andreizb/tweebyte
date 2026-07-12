/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ro.tweebyte.userservice.model.UserDto;
import ro.tweebyte.userservice.model.UserUpdateRequest;
import ro.tweebyte.userservice.service.UserService;

@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
public class UserController {

	// Upper bound on a single search page so a hostile/absurd ?size= can never ask the DB
	// for an unbounded result set; mirrors the tweet-service search controller's clamp.
	private static final int MAX_PAGE_SIZE = 10_000;

	private final UserService userService;

	private static int normalizePage(int page) {
		return Math.max(page, 0);
	}

	private static int normalizeSize(int size) {
		return Math.clamp(size, 1, MAX_PAGE_SIZE);
	}

	@GetMapping("/{userId}")
	public CompletableFuture<UserDto> getUserProfile(@PathVariable("userId") UUID userId) {
		return this.userService.getUserProfile(userId);
	}

	@GetMapping("/summary/{userId}")
	public CompletableFuture<UserDto> getUserSummary(@PathVariable("userId") UUID userId) {
		return this.userService.getUserSummary(userId);
	}

	// Batched summary read for the following-cache miss-path fill: one POST over a list of
	// ids returns each user's summary row, replacing the per-cold-user GET /summary/{id} the
	// interaction-service issued one-per-user. The id rides inside each row (UserDto carries
	// it) and the response is a plain JSON array — not a UUID-keyed map, which would churn
	// Jackson's symbol table at million-scale ids. Unknown ids are simply absent from the
	// array; the caller fills them from its own read-through. The single-id endpoint stays.
	@PostMapping("/summaries")
	public CompletableFuture<List<UserDto>> getUserSummaries(@RequestBody List<UUID> userIds) {
		return this.userService.getUserSummaries(userIds);
	}

	@GetMapping("/summary/name/{userName}")
	public CompletableFuture<UserDto> getUserSummaryByUserName(@PathVariable("userName") String userName) {
		return this.userService.getUserSummaryByUserName(userName);
	}

	@GetMapping("/search/{searchTerm}")
	public CompletableFuture<List<UserDto>> searchUser(@PathVariable("searchTerm") String searchTerm,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "10") int size) {
		return this.userService.searchUser(searchTerm, normalizePage(page), normalizeSize(size));
	}

	@PutMapping(path = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public CompletableFuture<Void> updateUser(@PathVariable("userId") UUID userId,
			@ModelAttribute UserUpdateRequest userUpdateRequest) {
		return this.userService.updateUser(userId, userUpdateRequest);
	}

	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public CompletableFuture<Void> deleteUser(@PathVariable("userId") UUID userId) {
		return this.userService.deleteUser(userId);
	}

}
