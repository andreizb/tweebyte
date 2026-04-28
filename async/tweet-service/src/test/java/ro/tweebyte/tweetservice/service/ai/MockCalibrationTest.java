package ro.tweebyte.tweetservice.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockCalibrationTest {

    private static final double TTFT = 250.0;
    private static final double SIGMA = 0.4;
    private static final double ITL = 40.0;
    private static final double SHAPE = 2.5;
    private static final double P_BURST = 0.0;

    @Test
    void blankPathFallsBackToDefaults() {
        MockCalibration cal = MockCalibration.loadOrDefault("", TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals(TTFT, cal.ttftMeanMs());
        assertEquals(SIGMA, cal.ttftLogSigma());
        assertEquals(ITL, cal.itlMeanMs());
        assertEquals(SHAPE, cal.itlGammaShape());
        assertEquals(P_BURST, cal.itlPBurst());
        assertEquals("defaults:property-unset", cal.source());
    }

    @Test
    void nullPathFallsBackToDefaults() {
        MockCalibration cal = MockCalibration.loadOrDefault(null, TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals("defaults:property-unset", cal.source());
    }

    @Test
    void missingFileFallsBackToDefaults(@TempDir Path tmp) {
        Path nonExistent = tmp.resolve("does-not-exist.json");
        MockCalibration cal = MockCalibration.loadOrDefault(nonExistent.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals(TTFT, cal.ttftMeanMs());
        assertTrue(cal.source().startsWith("defaults:file-missing:"));
    }

    @Test
    void incompleteJsonFallsBackToDefaults(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("incomplete.json");
        Files.writeString(bad, "{\"ttft_fits\":{\"lognormal\":{\"mu\":5.0}}}"); // missing sigma, gamma
        MockCalibration cal = MockCalibration.loadOrDefault(bad.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals("defaults:json-incomplete", cal.source());
        assertEquals(TTFT, cal.ttftMeanMs());
    }

    @Test
    void parseErrorFallsBackToDefaults(@TempDir Path tmp) throws Exception {
        Path garbage = tmp.resolve("garbage.json");
        Files.writeString(garbage, "this is not { valid } JSON at all");
        MockCalibration cal = MockCalibration.loadOrDefault(garbage.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals("defaults:parse-error", cal.source());
        assertEquals(TTFT, cal.ttftMeanMs());
    }

    @Test
    void validJsonLoadsFittedParameters(@TempDir Path tmp) throws Exception {
        Path good = tmp.resolve("calibration.json");
        Files.writeString(good,
                "{\"ttft_fits\":{\"lognormal\":{\"mu\":5.5,\"sigma\":0.6}}," +
                " \"itl_fits\":{\"gamma\":{\"shape\":3.0,\"scale\":20.0}}}");
        MockCalibration cal = MockCalibration.loadOrDefault(good.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        // E[log-normal] = exp(mu + sigma^2/2) = exp(5.5 + 0.18) = exp(5.68) ≈ 293.0
        assertEquals(Math.exp(5.5 + 0.5 * 0.6 * 0.6), cal.ttftMeanMs(), 1e-6);
        assertEquals(0.6, cal.ttftLogSigma(), 1e-9);
        // E[gamma] = shape * scale = 60
        assertEquals(60.0, cal.itlMeanMs(), 1e-9);
        assertEquals(3.0, cal.itlGammaShape(), 1e-9);
        // p_burst absent in JSON → 0.0 default
        assertEquals(0.0, cal.itlPBurst(), 1e-9);
        assertTrue(cal.source().startsWith("calibration.json:"));
    }

    @Test
    void validJsonWithPBurstLoadsZeroInflation(@TempDir Path tmp) throws Exception {
        Path good = tmp.resolve("calibration.json");
        Files.writeString(good,
                "{\"ttft_fits\":{\"lognormal\":{\"mu\":7.6,\"sigma\":0.3}}," +
                " \"itl_fits\":{\"p_burst\":0.384,\"gamma\":{\"shape\":0.86,\"scale\":10.4}}}");
        MockCalibration cal = MockCalibration.loadOrDefault(good.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals(0.384, cal.itlPBurst(), 1e-9);
        assertEquals(0.86, cal.itlGammaShape(), 1e-9);
    }

    @Test
    void pBurstClampedToUnitInterval(@TempDir Path tmp) throws Exception {
        Path tooHigh = tmp.resolve("high.json");
        Files.writeString(tooHigh,
                "{\"ttft_fits\":{\"lognormal\":{\"mu\":7.6,\"sigma\":0.3}}," +
                " \"itl_fits\":{\"p_burst\":1.5,\"gamma\":{\"shape\":0.86,\"scale\":10.4}}}");
        MockCalibration cal = MockCalibration.loadOrDefault(tooHigh.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals(1.0, cal.itlPBurst(), 1e-9);

        Path negative = tmp.resolve("negative.json");
        Files.writeString(negative,
                "{\"ttft_fits\":{\"lognormal\":{\"mu\":7.6,\"sigma\":0.3}}," +
                " \"itl_fits\":{\"p_burst\":-0.2,\"gamma\":{\"shape\":0.86,\"scale\":10.4}}}");
        MockCalibration cal2 = MockCalibration.loadOrDefault(negative.toString(),
                TTFT, SIGMA, ITL, SHAPE, P_BURST);
        assertEquals(0.0, cal2.itlPBurst(), 1e-9);
    }
}
