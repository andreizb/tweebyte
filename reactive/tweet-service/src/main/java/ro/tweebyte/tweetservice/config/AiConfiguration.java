package ro.tweebyte.tweetservice.config;

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

import java.net.URI;

/**
 * Wires the {@link ChatModel} bean the controllers / ChatClient consume.
 * See the async stack's AiConfiguration for the full contract.
 */
@Configuration
@Slf4j
public class AiConfiguration {

    @Value("${spring.ai.openai.base-url:}")
    private String openAiBaseUrl;

    @Value("${app.ai.backend:mock}")
    private String aiBackend;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.ai.backend", havingValue = "mock", matchIfMissing = true)
    public ChatModel mockChatModel(
            @Value("${app.ai.mock.ttft-mean-ms:250}") double ttftMeanMs,
            @Value("${app.ai.mock.ttft-log-sigma:0.4}") double ttftLogSigma,
            @Value("${app.ai.mock.itl-mean-ms:40}") double itlMeanMs,
            @Value("${app.ai.mock.itl-gamma-shape:2.5}") double itlGammaShape,
            @Value("${app.ai.mock.itl-p-burst:0.0}") double itlPBurst,
            @Value("${app.ai.mock.tokens-per-response:150}") int tokensPerResponse,
            @Value("${app.ai.mock.calibration-json:}") String calibrationJsonPath) {
        MockCalibration cal = MockCalibration.loadOrDefault(calibrationJsonPath,
                ttftMeanMs, ttftLogSigma, itlMeanMs, itlGammaShape, itlPBurst);
        log.info("MockStreamingChatModel active (source={}): ttftMean={}ms ttftSigma={} itlMean={}ms itlShape={} pBurst={} tokens={}",
                cal.source(), cal.ttftMeanMs(), cal.ttftLogSigma(),
                cal.itlMeanMs(), cal.itlGammaShape(), cal.itlPBurst(), tokensPerResponse);
        return new MockStreamingChatModel(cal.ttftMeanMs(), cal.ttftLogSigma(),
                cal.itlMeanMs(), cal.itlGammaShape(), cal.itlPBurst(), tokensPerResponse);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @PostConstruct
    public void warnIfRemoteOpenAi() {
        if (!"live".equalsIgnoreCase(aiBackend)) return;
        if (openAiBaseUrl == null || openAiBaseUrl.isBlank()) return;
        try {
            String host = URI.create(openAiBaseUrl).getHost();
            boolean loopback = host != null && (
                    host.equals("localhost") ||
                    host.equals("127.0.0.1") ||
                    host.equals("host.docker.internal") ||
                    host.endsWith(".localhost"));
            if (!loopback) {
                log.warn("WARNING: AI_BACKEND=live but spring.ai.openai.base-url={} does NOT look like a local endpoint. Refusing to silently route benchmark traffic to a remote provider. Verify LIVE_LLM_BASE_URL.",
                        openAiBaseUrl);
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Could not parse spring.ai.openai.base-url='{}': {}", openAiBaseUrl, ex.toString());
        }
    }
}
