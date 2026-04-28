package ro.tweebyte.tweetservice.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

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
