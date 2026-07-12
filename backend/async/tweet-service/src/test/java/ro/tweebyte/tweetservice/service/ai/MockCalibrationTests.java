/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.tweetservice.service.ai;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MockCalibrationTests {

	private static final double TTFT = 250.0;

	private static final double SIGMA = 0.4;

	private static final double ITL = 40.0;

	private static final double SHAPE = 2.5;

	private static final double P_BURST = 0.0;

	@Test
	void blankPathFallsBackToDefaults() {
		MockCalibration cal = MockCalibration.loadOrDefault("", TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.ttftMeanMs()).isEqualTo(TTFT);
		assertThat(cal.ttftLogSigma()).isEqualTo(SIGMA);
		assertThat(cal.itlMeanMs()).isEqualTo(ITL);
		assertThat(cal.itlGammaShape()).isEqualTo(SHAPE);
		assertThat(cal.itlPBurst()).isEqualTo(P_BURST);
		assertThat(cal.source()).isEqualTo("defaults:property-unset");
	}

	@Test
	void nullPathFallsBackToDefaults() {
		MockCalibration cal = MockCalibration.loadOrDefault(null, TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.source()).isEqualTo("defaults:property-unset");
	}

	@Test
	void missingFileFallsBackToDefaults(@TempDir Path tmp) {
		Path nonExistent = tmp.resolve("does-not-exist.json");
		MockCalibration cal = MockCalibration.loadOrDefault(nonExistent.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.ttftMeanMs()).isEqualTo(TTFT);
		assertThat(cal.source()).startsWith("defaults:file-missing:");
	}

	@Test
	void incompleteJsonFallsBackToDefaults(@TempDir Path tmp) throws Exception {
		Path bad = tmp.resolve("incomplete.json");
		Files.writeString(bad, "{\"ttft_fits\":{\"lognormal\":{\"mu\":5.0}}}"); // missing
																				// sigma,
																				// gamma
		MockCalibration cal = MockCalibration.loadOrDefault(bad.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.source()).isEqualTo("defaults:json-incomplete");
		assertThat(cal.ttftMeanMs()).isEqualTo(TTFT);
	}

	@Test
	void parseErrorFallsBackToDefaults(@TempDir Path tmp) throws Exception {
		Path garbage = tmp.resolve("garbage.json");
		Files.writeString(garbage, "this is not { valid } JSON at all");
		MockCalibration cal = MockCalibration.loadOrDefault(garbage.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.source()).isEqualTo("defaults:parse-error");
		assertThat(cal.ttftMeanMs()).isEqualTo(TTFT);
	}

	@Test
	void validJsonLoadsFittedParameters(@TempDir Path tmp) throws Exception {
		Path good = tmp.resolve("calibration.json");
		Files.writeString(good, "{\"ttft_fits\":{\"lognormal\":{\"mu\":5.5,\"sigma\":0.6}},"
				+ " \"itl_fits\":{\"gamma\":{\"shape\":3.0,\"scale\":20.0}}}");
		MockCalibration cal = MockCalibration.loadOrDefault(good.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		// E[log-normal] = exp(mu + sigma^2/2) = exp(5.5 + 0.18) = exp(5.68) ≈ 293.0
		assertThat(cal.ttftMeanMs()).isCloseTo(Math.exp(5.5 + 0.5 * 0.6 * 0.6), within(1e-6));
		assertThat(cal.ttftLogSigma()).isCloseTo(0.6, within(1e-9));
		// E[gamma] = shape * scale = 60
		assertThat(cal.itlMeanMs()).isCloseTo(60.0, within(1e-9));
		assertThat(cal.itlGammaShape()).isCloseTo(3.0, within(1e-9));
		// p_burst absent in JSON → 0.0 default
		assertThat(cal.itlPBurst()).isCloseTo(0.0, within(1e-9));
		assertThat(cal.source()).startsWith("calibration.json:");
	}

	@Test
	void validJsonWithPBurstLoadsZeroInflation(@TempDir Path tmp) throws Exception {
		Path good = tmp.resolve("calibration.json");
		Files.writeString(good, "{\"ttft_fits\":{\"lognormal\":{\"mu\":7.6,\"sigma\":0.3}},"
				+ " \"itl_fits\":{\"p_burst\":0.384,\"gamma\":{\"shape\":0.86,\"scale\":10.4}}}");
		MockCalibration cal = MockCalibration.loadOrDefault(good.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.itlPBurst()).isCloseTo(0.384, within(1e-9));
		assertThat(cal.itlGammaShape()).isCloseTo(0.86, within(1e-9));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({
			"only-mu-missing.json, '{\"ttft_fits\":{\"lognormal\":{\"sigma\":0.6}}, \"itl_fits\":{\"gamma\":{\"shape\":3.0,\"scale\":20.0}}}'",
			"only-sigma-missing.json, '{\"ttft_fits\":{\"lognormal\":{\"mu\":5.5}}, \"itl_fits\":{\"gamma\":{\"shape\":3.0,\"scale\":20.0}}}'",
			"only-shape-missing.json, '{\"ttft_fits\":{\"lognormal\":{\"mu\":5.5,\"sigma\":0.6}}, \"itl_fits\":{\"gamma\":{\"scale\":20.0}}}'",
			"only-scale-missing.json, '{\"ttft_fits\":{\"lognormal\":{\"mu\":5.5,\"sigma\":0.6}}, \"itl_fits\":{\"gamma\":{\"shape\":3.0}}}'" })
	void partialFitParametersFallBackToDefaults(String fileName, String json, @TempDir Path tmp) throws Exception {
		Path bad = tmp.resolve(fileName);
		Files.writeString(bad, json);
		MockCalibration cal = MockCalibration.loadOrDefault(bad.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.source()).isEqualTo("defaults:json-incomplete");
	}

	@Test
	void pBurstClampedToUnitInterval(@TempDir Path tmp) throws Exception {
		Path tooHigh = tmp.resolve("high.json");
		Files.writeString(tooHigh, "{\"ttft_fits\":{\"lognormal\":{\"mu\":7.6,\"sigma\":0.3}},"
				+ " \"itl_fits\":{\"p_burst\":1.5,\"gamma\":{\"shape\":0.86,\"scale\":10.4}}}");
		MockCalibration cal = MockCalibration.loadOrDefault(tooHigh.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal.itlPBurst()).isCloseTo(1.0, within(1e-9));

		Path negative = tmp.resolve("negative.json");
		Files.writeString(negative, "{\"ttft_fits\":{\"lognormal\":{\"mu\":7.6,\"sigma\":0.3}},"
				+ " \"itl_fits\":{\"p_burst\":-0.2,\"gamma\":{\"shape\":0.86,\"scale\":10.4}}}");
		MockCalibration cal2 = MockCalibration.loadOrDefault(negative.toString(), TTFT, SIGMA, ITL, SHAPE, P_BURST);
		assertThat(cal2.itlPBurst()).isCloseTo(0.0, within(1e-9));
	}

}
