/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import ro.tweebyte.userservice.service.MediaService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = MediaController.class,
		excludeAutoConfiguration = { ReactiveSecurityAutoConfiguration.class,
				ReactiveUserDetailsServiceAutoConfiguration.class })
class MediaControllerTests {

	@Autowired
	private WebTestClient webTestClient;

	@MockBean
	private MediaService mediaService;

	@Test
	void downloadReturnsPartialContentWhenServiceCompletes() {
		given(this.mediaService.download(any(), any())).willReturn(Mono.empty());

		this.webTestClient.get().uri("/media/{id}", UUID.randomUUID()).exchange().expectStatus().isEqualTo(206);
	}

	@Test
	void downloadPropagatesServiceFailureAs500() {
		given(this.mediaService.download(any(), any())).willReturn(Mono.error(new RuntimeException("io fail")));

		this.webTestClient.get().uri("/media/{id}", UUID.randomUUID()).exchange().expectStatus().is5xxServerError();
	}

}
