package ro.tweebyte.calibration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "collect",
        description = "Drive LM Studio to collect TTFT + ITL samples, fit distributions, write calibration.json.")
public class CollectSamplesCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--base-url", defaultValue = "http://localhost:1234",
            description = "LM Studio OpenAI-compatible base URL")
    String baseUrl;

    @CommandLine.Option(names = "--model", defaultValue = "qwen3.5-4b-mlx",
            description = "Model identifier as exposed by LM Studio on /v1/models (not the HF repo path).")
    String model;

    @CommandLine.Option(names = "--samples", defaultValue = "2000",
            description = "Number of samples to collect")
    int samples;

    @CommandLine.Option(names = "--prompt", defaultValue = "Summarize recent activity.",
            description = "Fixed prompt to use for every sample (keeps prompt length constant)")
    String prompt;

    @CommandLine.Option(names = "--max-tokens", defaultValue = "150",
            description = "Max tokens per response")
    int maxTokens;

    @CommandLine.Option(names = "--temperature", defaultValue = "0.7")
    double temperature;

    @CommandLine.Option(names = "--out", defaultValue = "testing/calibration/calibration.json",
            description = "Output JSON path for fitted parameters")
    String outPath;

    @Override
    public Integer call() throws Exception {
        System.out.printf("Collecting %d samples from %s (%s)...%n", samples, baseUrl, model);
        ObjectMapper mapper = new ObjectMapper();
        List<Double> ttftMs = new ArrayList<>(samples);
        List<Double> itlMs = new ArrayList<>();
        List<Integer> tokenCounts = new ArrayList<>(samples);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        long wallStart = System.nanoTime();
        for (int i = 0; i < samples; i++) {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("stream", true);
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);
            payload.putArray("messages").addObject().put("role", "user").put("content", prompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer lm-studio")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            long start = System.nanoTime();
            HttpResponse<java.io.InputStream> response;
            try {
                response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (java.io.IOException e) {
                System.err.printf("Sample %d send failed: %s%n", i, e.getMessage());
                System.err.flush();
                continue;
            }
            if (response.statusCode() >= 400) {
                System.err.printf("Sample %d failed status=%d%n", i, response.statusCode());
                System.err.flush();
                continue;
            }

            long firstToken = -1;
            long lastToken = -1;
            int tokens = 0;
            // Per-sample wall-clock budget: bail if the response stream stalls past this.
            // LM Studio can drop the stream without sending [DONE], leaving the body input
            // stream parked indefinitely on ArrayBlockingQueue.take(). The HttpRequest
            // timeout above only covers connect+headers; once the body stream is open,
            // the read can hang forever. Watch the wall clock and break out instead.
            long sampleDeadlineNanos = start + Duration.ofSeconds(90).toNanos();
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (System.nanoTime() > sampleDeadlineNanos) {
                        System.err.printf("Sample %d body read exceeded 90s; breaking%n", i);
                        System.err.flush();
                        break;
                    }
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) continue;
                    JsonNode event = mapper.readTree(data);
                    // Qwen3.5-specific note: this model has a "reasoning mode" that emits
                    // delta.reasoning_content chunks (or delta.reasoning under mlx_lm.server)
                    // instead of delta.content. Spring AI's OpenAI client filters those out
                    // as non-content, so user-facing TTFT/ITL must be measured against
                    // delta.content only — that's what the SUT consumer sees. This means a
                    // Qwen-mode sample where reasoning consumes the entire max_tokens budget
                    // contributes a token_count=0 row to the calibration (see §5.2 — 27/2000
                    // such samples in the original collection). For non-reasoning models this
                    // distinction is moot.
                    JsonNode delta = event.path("choices").path(0).path("delta").path("content");
                    if (!delta.isTextual() || delta.asText().isEmpty()) continue;
                    long now = System.nanoTime();
                    if (firstToken < 0) {
                        firstToken = now;
                        ttftMs.add((now - start) / 1_000_000.0);
                    } else {
                        itlMs.add((now - lastToken) / 1_000_000.0);
                    }
                    lastToken = now;
                    tokens++;
                }
            } catch (java.io.IOException e) {
                System.err.printf("Sample %d body read failed: %s%n", i, e.getMessage());
                System.err.flush();
            }
            tokenCounts.add(tokens);
            if ((i + 1) % 25 == 0) {
                double secPerSample = (System.nanoTime() - wallStart) / 1e9 / (i + 1);
                System.out.printf("  collected %d / %d samples (%.2fs/sample, ttft_n=%d, itl_n=%d)%n",
                        i + 1, samples, secPerSample, ttftMs.size(), itlMs.size());
                System.out.flush();
            }
        }
        double wallSec = (System.nanoTime() - wallStart) / 1e9;
        System.out.printf("Collection done in %.1fs; TTFT n=%d, ITL n=%d%n",
                wallSec, ttftMs.size(), itlMs.size());
        System.out.flush();

        ObjectNode out = mapper.createObjectNode();
        out.put("collected_at", java.time.Instant.now().toString());
        out.put("base_url", baseUrl);
        out.put("model", model);
        out.put("prompt", prompt);
        out.put("samples_target", samples);
        out.put("ttft_ms_observed", ttftMs.size());
        out.put("itl_ms_observed", itlMs.size());

        out.set("ttft_fits", FitUtil.fitAll(ttftMs, mapper, 0.0));
        // ITL distributions are bimodal: TCP/SSE coalescing produces a near-zero
        // intra-burst spike (~38% mass) plus a real model-emission gap-mode
        // (~62% mass at ~10 ms median). Fit only the gap-mode subset so the
        // log-normal/gamma/Weibull parameters describe the model's actual
        // emission timing, and emit p_burst alongside so MockStreamingChatModel
        // can reproduce the bimodality via zero inflation while preserving
        // E[response]. The 0.1 ms threshold is well below any realistic Qwen
        // ITL (its real median is ~8.76 ms) so cutting there cleanly separates
        // the two modes.
        out.set("itl_fits", FitUtil.fitAll(itlMs, mapper, FitUtil.ITL_BURST_THRESHOLD_MS));
        out.set("token_counts", mapper.valueToTree(tokenCounts));
        out.set("ttft_samples", mapper.valueToTree(ttftMs));
        out.set("itl_samples", mapper.valueToTree(itlMs));

        Path target = Path.of(outPath);
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.writeString(target, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.printf("Wrote %s%n", target.toAbsolutePath());
        return 0;
    }

}
