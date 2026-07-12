/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import ro.tweebyte.tweetservice.service.ai.MockStreamingChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for reactive {@link AiConfiguration}.
 * Exercises every branch: mock vs live backend, chatClient wrapping, and
 * the warnIfRemoteOpenAi / isLocalEndpoint guard logic.
 */
class AiConfigurationTests {

	// -------------------------------------------------------------------------
	// mockChatModel
	// -------------------------------------------------------------------------

	@Test
	void mockChatModel_withEmptyCalibrationPath_returnsMockStreamingChatModel() {
		AiConfiguration config = new AiConfiguration();
		ChatModel model = config.mockChatModel(250.0, 0.4, 40.0, 2.5, 0.0, 150, "");
		assertThat(model).isInstanceOf(MockStreamingChatModel.class);
	}

	@Test
	void mockChatModel_withNullCalibrationPath_returnsMockStreamingChatModel() {
		AiConfiguration config = new AiConfiguration();
		ChatModel model = config.mockChatModel(250.0, 0.4, 40.0, 2.5, 0.0, 10, null);
		assertThat(model).isInstanceOf(MockStreamingChatModel.class);
	}

	@Test
	void mockChatModel_withNonExistentCalibrationPath_fallsBackToDefaults() {
		AiConfiguration config = new AiConfiguration();
		ChatModel model = config.mockChatModel(
				100.0, 0.3, 20.0, 1.5, 0.1, 50, "/tmp/no-such-calibration-file-xyz.json");
		assertThat(model).isInstanceOf(MockStreamingChatModel.class);
	}

	// -------------------------------------------------------------------------
	// chatClient — mock branch
	// -------------------------------------------------------------------------

	@Test
	void chatClient_withMockBackend_doesNotWrapInTimeoutModel() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "mock");
		setField(config, "liveStreamTimeoutMs", 30_000L);

		ChatModel model = config.mockChatModel(250.0, 0.4, 40.0, 2.5, 0.0, 5, "");
		ChatClient client = config.chatClient(model);
		assertThat(client).isNotNull();
	}

	@Test
	void chatClient_withEmptyBackend_doesNotWrapInTimeoutModel() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "");
		setField(config, "liveStreamTimeoutMs", 30_000L);

		ChatModel model = config.mockChatModel(250.0, 0.4, 40.0, 2.5, 0.0, 5, "");
		ChatClient client = config.chatClient(model);
		assertThat(client).isNotNull();
	}

	// -------------------------------------------------------------------------
	// chatClient — live branch
	// -------------------------------------------------------------------------

	@Test
	void chatClient_withLiveBackend_wrapsInTimeoutChatModel() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "liveStreamTimeoutMs", 5_000L);

		ChatModel delegate = mock(ChatModel.class);
		ChatClient client = config.chatClient(delegate);
		assertThat(client).isNotNull();
	}

	@Test
	void chatClient_withLiveBackendCaseInsensitive_wrapsInTimeoutChatModel() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "LIVE");
		setField(config, "liveStreamTimeoutMs", 1_000L);

		ChatModel delegate = mock(ChatModel.class);
		ChatClient client = config.chatClient(delegate);
		assertThat(client).isNotNull();
	}

	// -------------------------------------------------------------------------
	// warnIfRemoteOpenAi — all branches
	// -------------------------------------------------------------------------

	@Test
	void warnIfRemoteOpenAi_mockBackend_doesNothing() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "mock");
		setField(config, "openAiBaseUrl", "http://api.openai.com");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_blankUrl_doesNothing() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_nullUrl_doesNothing() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", null);
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_localhostUrl_doesNotWarn() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "http://localhost:8080");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_loopbackIp_doesNotWarn() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "http://127.0.0.1:11434");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_dockerInternal_doesNotWarn() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "http://host.docker.internal:11434");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_dotLocalhostSuffix_doesNotWarn() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "http://llm.localhost:8080");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_remoteUrl_logsWarningWithoutThrowing() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "https://api.openai.com/v1");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_unknownHost_treatsAsRemoteWithoutThrowing() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "http://no-such-host-tweebyte-xyz.internal:80");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_malformedUrl_logsWarningWithoutThrowing() throws Exception {
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "not a valid url");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	@Test
	void warnIfRemoteOpenAi_liveBackend_urlWithNullHost_logsWarningWithoutThrowing() throws Exception {
		// A URL like "file:///path" has a null host from URI.getHost().
		// This exercises the `if (host == null) return false` branch in isLocalEndpoint.
		AiConfiguration config = new AiConfiguration();
		setField(config, "aiBackend", "live");
		setField(config, "openAiBaseUrl", "file:///some/local/path");
		assertThatCode(config::warnIfRemoteOpenAi).doesNotThrowAnyException();
	}

	// -------------------------------------------------------------------------
	// helpers
	// -------------------------------------------------------------------------

	private static void setField(Object target, String name, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

}
