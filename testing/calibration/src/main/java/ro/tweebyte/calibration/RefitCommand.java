package ro.tweebyte.calibration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Re-fit an existing {@code calibration.json} in place using the latest
 * {@link FitUtil} logic — useful when the fitter changes (e.g. when the
 * burst-mode threshold is added or tweaked) and the operator does not want
 * to re-collect samples (each collect run is ~1.5 h against Qwen 3.5).
 *
 * <p>Reads the {@code ttft_samples} and {@code itl_samples} arrays from the
 * input JSON, regenerates {@code ttft_fits} and {@code itl_fits} (the latter
 * with {@code p_burst} and gap-mode subset stats), and writes the result
 * back to the same path. Other fields (collected_at, base_url, model,
 * prompt, samples_target, ttft_ms_observed, itl_ms_observed, token_counts)
 * are preserved verbatim, plus a {@code refitted_at} timestamp is added.
 */
@CommandLine.Command(
        name = "refit",
        description = "Re-fit an existing calibration.json in place using the current fitter logic.")
public class RefitCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--calibration", defaultValue = "testing/calibration/calibration.json",
            description = "Path to calibration JSON to re-fit (read and overwritten in place)")
    String calibrationPath;

    @CommandLine.Option(names = "--itl-burst-threshold-ms",
            description = "Override the ITL burst threshold (default: " + 0.1 + " ms)")
    Double itlBurstThresholdMs;

    @Override
    public Integer call() throws Exception {
        Path path = Path.of(calibrationPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(path));
        if (!root.isObject()) {
            System.err.println("Calibration JSON root is not an object: " + path);
            return 2;
        }
        ObjectNode out = (ObjectNode) root;

        List<Double> ttft = extractSamples(root.path("ttft_samples"));
        List<Double> itl = extractSamples(root.path("itl_samples"));
        if (ttft.isEmpty() || itl.isEmpty()) {
            System.err.println("Calibration JSON missing ttft_samples / itl_samples arrays; nothing to refit.");
            return 2;
        }

        double burstThreshold = itlBurstThresholdMs != null ? itlBurstThresholdMs : FitUtil.ITL_BURST_THRESHOLD_MS;

        out.set("ttft_fits", FitUtil.fitAll(ttft, mapper, 0.0));
        out.set("itl_fits", FitUtil.fitAll(itl, mapper, burstThreshold));
        out.put("refitted_at", java.time.Instant.now().toString());

        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        JsonNode itlFits = out.path("itl_fits");
        System.out.printf("Refitted %s (TTFT n=%d, ITL n=%d).%n", path.toAbsolutePath(),
                ttft.size(), itl.size());
        if (itlFits.has("p_burst")) {
            System.out.printf("ITL bimodality: p_burst=%.4f (%d samples below %.3fms), gap-mode n=%d, gap-mode mean=%.3fms%n",
                    itlFits.path("p_burst").asDouble(),
                    itlFits.path("burst_count").asLong(),
                    itlFits.path("burst_threshold_ms").asDouble(),
                    itlFits.path("gap_n").asLong(),
                    itlFits.path("gap_mean").asDouble(Double.NaN));
        }
        JsonNode gamma = itlFits.path("gamma");
        if (gamma.has("shape")) {
            System.out.printf("ITL gamma fit (gap-mode): shape=%.4f, scale=%.4f, mean=%.3fms%n",
                    gamma.path("shape").asDouble(),
                    gamma.path("scale").asDouble(),
                    gamma.path("shape").asDouble() * gamma.path("scale").asDouble());
        }
        return 0;
    }

    private List<Double> extractSamples(JsonNode arrayNode) {
        List<Double> out = new ArrayList<>();
        if (!arrayNode.isArray()) return out;
        for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
            out.add(it.next().asDouble());
        }
        return out;
    }
}
