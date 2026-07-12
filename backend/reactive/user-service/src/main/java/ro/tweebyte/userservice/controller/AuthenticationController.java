/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.model.AuthenticationResponse;
import ro.tweebyte.userservice.model.UserLoginRequest;
import ro.tweebyte.userservice.model.UserRegisterRequest;
import ro.tweebyte.userservice.service.AuthenticationService;

@RestController
@RequestMapping(path = "/auth")
@AllArgsConstructor
@ConditionalOnProperty(prefix = "app.keycloak", name = "enabled", havingValue = "true")
public class AuthenticationController {

	private final AuthenticationService authenticationService;

	// Multipart so the registration form can carry text fields plus an optional
	// profilePictureId; the picture bytes themselves are uploaded separately via
	// image-upload and only the resulting asset id is bound here.
	@PostMapping(path = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<AuthenticationResponse> userRegister(@Valid @ModelAttribute UserRegisterRequest request) {
		return this.authenticationService.register(request);
	}

	@PostMapping(path = "/login")
	public Mono<AuthenticationResponse> userLogin(@Valid @RequestBody UserLoginRequest request) {
		return this.authenticationService.login(request);
	}

}
