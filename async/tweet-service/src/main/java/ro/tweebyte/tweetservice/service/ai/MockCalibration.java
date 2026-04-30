package ro.tweebyte.tweetservice.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immutable record of mock-model distribution parameters plus a loader that
 * pulls from {@code calibration.json} (emitted by the testing/calibration
 * module) when available, else falls back to defaults supplied by the caller.
 *
 * <p>Layout expected in calibration.json:
 * <pre>
 *   {
 *     "ttft_fits": { "lognormal": { "mu": ..., "sigma": ... } },
 *     "itl_fits":  {
 *       "p_burst":  ...,                       // optional, default 0.0
 *       "gamma":    { "shape": ..., "scale": ... }
 *     }
 *   }
 * </pre>
 *
 * <p>The {@code p_burst} field is the zero-inflation probability for the ITL
 * generator: with probability {@code p_burst} the mock emits a token with
 * zero delay (intra-burst arrival, modelling the case where multiple
 * {@code delta.content} chunks land in the same TCP frame and are read
 * back-to-back at memory speed by an HTTP/1.1 client). With probability
 * {@code 1 - p_burst} the inter-token interval is sampled from the gamma
 * fit, which is itself fitted only to gap-mode samples (ITL ≥ 0.1 ms) so
 * the mock reproduces both modes of the empirical bimodal distribution
 * while preserving E[response]. Calibration files without the
 * {@code p_burst} field are accepted with {@code p_burst = 0.0}, which
 * collapses the mock to pure-gamma behaviour.
 *
 * <p>Values are {@code double} throughout — {@code long} truncation of the
 * mean biased the log-normal centre under K-S, so we keep full precision
 * until {@code MockStreamingChatModel} samples it.
 */
@Slf4j
public record MockCalibration(double ttftMeanMs, double ttftLogSigma,
                              double itlMeanMs, double itlGammaShape,
                              double itlPBurst,
                              String source) {

    public static MockCalibration loadOrDefault(String jsonPath,
                                                double defaultTtftMeanMs,
                                                double defaultTtftLogSigma,
                                                double defaultItlMeanMs,
                                                double defaultItlGammaShape,
                                                double defaultItlPBurst) {
        if (jsonPath == null || jsonPath.isBlank()) {
            return defaultsWithSource(defaultTtftMeanMs, defaultTtftLogSigma,
                    defaultItlMeanMs, defaultItlGammaShape, defaultItlPBurst,
                    "defaults:property-unset");
        }
        Path path = Path.of(jsonPath);
        if (!Files.exists(path)) {
            log.warn("Calibration JSON not found at {}; using defaults.", path);
            return defaultsWithSource(defaultTtftMeanMs, defaultTtftLogSigma,
                    defaultItlMeanMs, defaultItlGammaShape, defaultItlPBurst,
                    "defaults:file-missing:" + path);
        }
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(path));
            JsonNode ttftFit = root.path("ttft_fits").path("lognormal");
            JsonNode itlFits = root.path("itl_fits");
            JsonNode itlFit = itlFits.path("gamma");
            double mu = ttftFit.path("mu").asDouble(Double.NaN);
            double sigma = ttftFit.path("sigma").asDouble(Double.NaN);
            double shape = itlFit.path("shape").asDouble(Double.NaN);
            double scale = itlFit.path("scale").asDouble(Double.NaN);
            // p_burst is optional. Calibration files without the field lack
            // the field; we read 0.0 and the mock falls back to pure-gamma ITL.
            double pBurst = clampPBurst(itlFits.path("p_burst").asDouble(0.0));
            if (Double.isNaN(mu) || Double.isNaN(sigma) || Double.isNaN(shape) || Double.isNaN(scale)) {
                log.warn("Calibration JSON at {} missing expected fields; using defaults.", path);
                return defaultsWithSource(defaultTtftMeanMs, defaultTtftLogSigma,
                        defaultItlMeanMs, defaultItlGammaShape, defaultItlPBurst,
                        "defaults:json-incomplete");
            }
            double ttftMean = Math.exp(mu + 0.5 * sigma * sigma);
            double itlMean = shape * scale;
            log.info("Loaded mock calibration from {}: ttftMean={}ms sigma={} itlMean={}ms shape={} pBurst={}",
                    path, ttftMean, sigma, itlMean, shape, pBurst);
            return new MockCalibration(ttftMean, sigma, itlMean, shape, pBurst,
                    "calibration.json:" + path);
        } catch (Exception e) {
            log.warn("Failed to parse calibration JSON at {}: {}; using defaults.", path, e.toString());
            return defaultsWithSource(defaultTtftMeanMs, defaultTtftLogSigma,
                    defaultItlMeanMs, defaultItlGammaShape, defaultItlPBurst,
                    "defaults:parse-error");
        }
    }

    private static MockCalibration defaultsWithSource(double ttftMean, double sigma,
                                                      double itlMean, double shape,
                                                      double pBurst, String source) {
        return new MockCalibration(ttftMean, sigma, itlMean, shape, clampPBurst(pBurst), source);
    }

    private static double clampPBurst(double v) {
        if (Double.isNaN(v) || v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
