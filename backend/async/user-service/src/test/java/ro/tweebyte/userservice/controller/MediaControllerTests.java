/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.controller;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ro.tweebyte.userservice.service.MediaService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MediaControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MediaService mediaService;

	@Test
	void downloadReturnsPartialContentForCachedAsset() throws Exception {
		UUID id = UUID.randomUUID();
		StreamingResponseBody body = out -> out.write(new byte[] { 1, 2, 3 });
		ResponseEntity<StreamingResponseBody> resp = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
			.header(HttpHeaders.CONTENT_RANGE, "bytes 0-2/3")
			.contentType(MediaType.TEXT_PLAIN)
			.body(body);
		given(this.mediaService.download(any())).willReturn(CompletableFuture.completedFuture(resp));

		MvcResult mvcResult = this.mockMvc.perform(get("/media/{id}", id))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult))
			.andExpect(status().isPartialContent())
			.andExpect(header().exists(HttpHeaders.CONTENT_RANGE));
	}

	@Test
	void downloadReturnsNotFoundWhenServiceReports404() throws Exception {
		UUID id = UUID.randomUUID();
		given(this.mediaService.download(any()))
			.willReturn(CompletableFuture.completedFuture(ResponseEntity.notFound().build()));

		MvcResult mvcResult = this.mockMvc.perform(get("/media/{id}", id))
			.andExpect(request().asyncStarted())
			.andReturn();

		this.mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isNotFound());
	}

}
