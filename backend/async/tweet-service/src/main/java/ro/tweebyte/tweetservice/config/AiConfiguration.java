/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import ro.tweebyte.tweetservice.service.ai.MockCalibration;
import ro.tweebyte.tweetservice.service.ai.MockStreamingChatModel;
import ro.tweebyte.tweetservice.service.ai.TimeoutChatModel;

/**
 * Wires the {@link ChatModel} bean the controllers / ChatClient consume.
 *
 * <ul>
 * <li>{@code app.ai.backend=mock} (default): register {@link MockStreamingChatModel} as
 * {@code @Primary}. Spring AI's OpenAI auto-config still runs but loses injection
 * priority. Effectively: all traffic hits the calibrated mock.</li>
 * <li>{@code app.ai.backend=live}: do not register the mock. Spring AI auto-configuration
 * provides {@code OpenAiChatModel} from {@code spring.ai.openai.*} — the base-url can
 * point at any OpenAI-compatible streaming endpoint (LM Studio, mlx_lm.server, Ollama,
 * vLLM, ...). The {@code LIVE_LLM_BASE_URL} env var controls the endpoint; see
 * deployment/docker-compose/{async,reactive}.yml for the full passthrough.</li>
 * </ul>
 *
 * @author Andrei Zbarcea
 */
@Configuration
@Slf4j
public class AiConfiguration {

	@Value("${spring.ai.openai.base-url:}")
	private String openAiBaseUrl;

	@Value("${app.ai.backend:mock}")
	private String aiBackend;

	@Value("${app.ai.live.stream-timeout-ms:30000}")
	private long liveStreamTimeoutMs;

	@Bean
	@Primary
	@ConditionalOnProperty(name = "app.ai.backend", havingValue = "mock", matchIfMissing = true)
	public ChatModel mockChatModel(@Value("${app.ai.mock.ttft-mean-ms:250}") double ttftMeanMs,
			@Value("${app.ai.mock.ttft-log-sigma:0.4}") double ttftLogSigma,
			@Value("${app.ai.mock.itl-mean-ms:40}") double itlMeanMs,
			@Value("${app.ai.mock.itl-gamma-shape:2.5}") double itlGammaShape,
			@Value("${app.ai.mock.itl-p-burst:0.0}") double itlPBurst,
			@Value("${app.ai.mock.tokens-per-response:150}") int tokensPerResponse,
			@Value("${app.ai.mock.calibration-json:}") String calibrationJsonPath) {
		MockCalibration cal = MockCalibration.loadOrDefault(calibrationJsonPath, ttftMeanMs, ttftLogSigma, itlMeanMs,
				itlGammaShape, itlPBurst);
		log.info(
				"MockStreamingChatModel active (source={}): ttftMean={}ms ttftSigma={} itlMean={}ms itlShape={} pBurst={} tokens={}",
				cal.source(), cal.ttftMeanMs(), cal.ttftLogSigma(), cal.itlMeanMs(), cal.itlGammaShape(),
				cal.itlPBurst(), tokensPerResponse);
		return new MockStreamingChatModel(cal.ttftMeanMs(), cal.ttftLogSigma(), cal.itlMeanMs(), cal.itlGammaShape(),
				cal.itlPBurst(), tokensPerResponse);
	}

	/**
	 * Builds the {@link ChatClient} the controllers consume. On the default/benchmark mock
	 * backend the {@code @Primary} {@link MockStreamingChatModel} flows through untouched, so
	 * the measured stream is byte-for-byte unchanged. Only when {@code app.ai.backend=live}
	 * is the model wrapped in a {@link TimeoutChatModel} so a stalled live endpoint fails
	 * fast instead of hanging the streaming worker forever.
	 * @param chatModel the active chat model ({@link MockStreamingChatModel} by default, or
	 * Spring AI's auto-configured {@code OpenAiChatModel} when live)
	 * @return the chat client wired to the (optionally timeout-guarded) model
	 */
	@Bean
	public ChatClient chatClient(ChatModel chatModel) {
		if (!"live".equalsIgnoreCase(this.aiBackend)) {
			return ChatClient.create(chatModel);
		}
		log.info("Live AI backend active: wrapping ChatModel with a {}ms stream deadline", this.liveStreamTimeoutMs);
		return ChatClient.create(new TimeoutChatModel(chatModel, Duration.ofMillis(this.liveStreamTimeoutMs)));
	}

	/**
	 * Startup safety check: shout loudly if the OpenAI base URL isn't pointing at a local
	 * backend when AI_BACKEND=live. Prevents a typo from routing benchmark traffic to
	 * api.openai.com with a real key.
	 */
	@PostConstruct
	public void warnIfRemoteOpenAi() {
		if (!"live".equalsIgnoreCase(this.aiBackend)) {
			return;
		}
		if (this.openAiBaseUrl == null || this.openAiBaseUrl.isBlank()) {
			return;
		}
		try {
			String host = URI.create(this.openAiBaseUrl).getHost();
			if (!isLocalEndpoint(host)) {
				log.warn(
						"WARNING: AI_BACKEND=live but spring.ai.openai.base-url={} is NOT a local endpoint — this app WILL route benchmark traffic to a remote provider. The native-local benchmark runner (ai-stream-summarize/run.sh) hard-refuses a non-loopback LIVE_LLM_BASE_URL; if you see this outside a deliberate AI_ALLOW_NON_LOCAL_LLM override, fix the wiring.",
						this.openAiBaseUrl);
			}
		}
		catch (IllegalArgumentException ex) {
			log.warn("Could not parse spring.ai.openai.base-url='{}': {}", this.openAiBaseUrl, ex.toString());
		}
	}

	private static boolean isLocalEndpoint(String host) {
		if (host == null) {
			return false;
		}
		if (host.equals("host.docker.internal") || host.endsWith(".localhost")) {
			return true;
		}
		try {
			return InetAddress.getByName(host).isLoopbackAddress();
		}
		catch (UnknownHostException ex) {
			return false;
		}
	}

}
