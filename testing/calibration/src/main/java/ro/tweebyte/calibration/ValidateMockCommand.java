package ro.tweebyte.calibration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "validate",
        description = "Validate the mock backend's TTFT + ITL distributions against observed real-LLM data via Kolmogorov-Smirnov.")
public class ValidateMockCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--calibration", defaultValue = "testing/calibration/calibration.json",
            description = "Path to calibration JSON produced by the `collect` subcommand")
    String calibrationPath;

    @CommandLine.Option(names = "--mock-samples", defaultValue = "10000",
            description = "Number of synthetic samples to draw from the mock")
    int mockSamples;

    @CommandLine.Option(names = "--ttft-mean-ms", defaultValue = "-1",
            description = "Override the log-normal fitted mu/sigma by supplying TTFT mean (ms). -1 means use the fit.")
    double ttftMeanOverride;

    @CommandLine.Option(names = "--ttft-log-sigma", defaultValue = "-1")
    double ttftSigmaOverride;

    @CommandLine.Option(names = "--itl-mean-ms", defaultValue = "-1")
    double itlMeanOverride;

    @CommandLine.Option(names = "--itl-gamma-shape", defaultValue = "-1")
    double itlShapeOverride;

    @CommandLine.Option(names = "--alpha", defaultValue = "0.05",
            description = "p-value threshold for accepting the mock's fit (K-S test)")
    double alpha;

    @CommandLine.Option(names = "--itl-family", defaultValue = "gamma",
            description = "Which family to use for the ITL synthetic draws: 'gamma' (production mock), 'shifted_lognormal' (3-parameter alternative), or 'lognormal_mixture' (5-parameter 2-component log-normal mixture). The non-gamma families are calibration-side analysis only; the production mock binary always reads the gamma fit. Default: gamma.")
    String itlFamily;

    @Override
    public Integer call() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode calibration = mapper.readTree(Files.readString(Path.of(calibrationPath)));

        double[] ttftReal = extractSamples(calibration.path("ttft_samples"));
        double[] itlReal = extractSamples(calibration.path("itl_samples"));
        if (ttftReal.length == 0 || itlReal.length == 0) {
            System.err.println("Calibration file is missing ttft_samples or itl_samples arrays.");
            return 2;
        }

        double mu, sigma;
        if (ttftSigmaOverride > 0 && ttftMeanOverride > 0) {
            mu = Math.log(ttftMeanOverride) - 0.5 * ttftSigmaOverride * ttftSigmaOverride;
            sigma = ttftSigmaOverride;
        } else {
            JsonNode lognormFit = calibration.path("ttft_fits").path("lognormal");
            mu = lognormFit.path("mu").asDouble();
            sigma = lognormFit.path("sigma").asDouble();
        }

        // Zero-inflation probability for the ITL mock; mirrors the
        // MockStreamingChatModel runtime behaviour. With prob p_burst we
        // emit a zero-delay token (intra-burst arrival) and otherwise
        // sample from the gap-mode fit. Pre-zero-inflation calibration files
        // (no p_burst field) pass through with p_burst=0.
        double pBurst = clampPBurst(calibration.path("itl_fits").path("p_burst").asDouble(0.0));

        double[] ttftMock = drawLogNormal(mu, sigma, mockSamples);

        // Two ITL paths: gamma (production mock) and shifted_lognormal
        // (calibration-side analysis only; not used by MockStreamingChatModel).
        double[] itlMock;
        String itlReport;
        boolean itlAccept;
        double itlP;
        KolmogorovSmirnovTest ks = new KolmogorovSmirnovTest();
        double ttftP = ks.kolmogorovSmirnovTest(ttftReal, ttftMock);

        if ("shifted_lognormal".equalsIgnoreCase(itlFamily)) {
            JsonNode shiftedFit = calibration.path("itl_fits").path("shifted_lognormal");
            if (!shiftedFit.has("c") || !shiftedFit.has("mu") || !shiftedFit.has("sigma")) {
                System.err.println("Calibration JSON's itl_fits.shifted_lognormal node is missing — refit first with the latest fitter.");
                return 2;
            }
            double c = shiftedFit.path("c").asDouble();
            double sMu = shiftedFit.path("mu").asDouble();
            double sSigma = shiftedFit.path("sigma").asDouble();
            itlMock = drawZeroInflatedShiftedLogNormal(c, sMu, sSigma, pBurst, mockSamples);
            itlP = ks.kolmogorovSmirnovTest(itlReal, itlMock);
            itlAccept = itlP >= alpha;
            itlReport = String.format(
                    "ITL  mock params (shifted_lognormal): c=%.4f mu=%.4f sigma=%.4f p_burst=%.4f  K-S p=%.4f  %s",
                    c, sMu, sSigma, pBurst, itlP, itlAccept ? "ACCEPT" : "REJECT");
        } else if ("lognormal_mixture".equalsIgnoreCase(itlFamily)) {
            JsonNode mixFit = calibration.path("itl_fits").path("lognormal_mixture_2");
            if (!mixFit.has("pi") || !mixFit.has("mu1") || !mixFit.has("mu2")) {
                System.err.println("Calibration JSON's itl_fits.lognormal_mixture_2 node is missing — refit first with the latest fitter.");
                return 2;
            }
            double mPi = mixFit.path("pi").asDouble();
            double mMu1 = mixFit.path("mu1").asDouble();
            double mSigma1 = mixFit.path("sigma1").asDouble();
            double mMu2 = mixFit.path("mu2").asDouble();
            double mSigma2 = mixFit.path("sigma2").asDouble();
            itlMock = drawZeroInflatedLogNormalMixture(mPi, mMu1, mSigma1, mMu2, mSigma2, pBurst, mockSamples);
            itlP = ks.kolmogorovSmirnovTest(itlReal, itlMock);
            itlAccept = itlP >= alpha;
            itlReport = String.format(
                    "ITL  mock params (lognormal_mixture_2): pi=%.4f mu1=%.4f sigma1=%.4f mu2=%.4f sigma2=%.4f p_burst=%.4f  K-S p=%.4f  %s",
                    mPi, mMu1, mSigma1, mMu2, mSigma2, pBurst, itlP, itlAccept ? "ACCEPT" : "REJECT");
        } else {
            double shape, scale;
            if (itlMeanOverride > 0 && itlShapeOverride > 0) {
                shape = itlShapeOverride;
                scale = itlMeanOverride / itlShapeOverride;
            } else {
                JsonNode gammaFit = calibration.path("itl_fits").path("gamma");
                shape = gammaFit.path("shape").asDouble();
                scale = gammaFit.path("scale").asDouble();
            }
            itlMock = drawZeroInflatedGamma(shape, scale, pBurst, mockSamples);
            itlP = ks.kolmogorovSmirnovTest(itlReal, itlMock);
            itlAccept = itlP >= alpha;
            itlReport = String.format(
                    "ITL  mock params (gamma): shape=%.4f scale=%.4f p_burst=%.4f  K-S p=%.4f  %s",
                    shape, scale, pBurst, itlP, itlAccept ? "ACCEPT" : "REJECT");
        }

        System.out.printf("TTFT mock params: mu=%.4f sigma=%.4f  K-S p=%.4f  %s%n",
                mu, sigma, ttftP, ttftP >= alpha ? "ACCEPT" : "REJECT");
        System.out.println(itlReport);

        boolean ok = ttftP >= alpha && itlAccept;
        if (ok) {
            System.out.println("Result: mock calibration (" + itlFamily + ") is statistically indistinguishable from real LLM at alpha=" + alpha);
            return 0;
        }
        System.out.println("Result: mock calibration (" + itlFamily + ") does NOT match real LLM at alpha=" + alpha);
        if ("gamma".equalsIgnoreCase(itlFamily)) {
            System.out.println("Note: For empirical samples with comb-quantized structure (e.g. Qwen3.5-4B-MLX gap-mode ITL — see RESULTS.md §5.2.1), no low-parameter smooth family — including the 3-parameter shifted log-normal and the 5-parameter 2-component log-normal mixture — has been observed to pass K-S at the calibration source's n. Treat the gamma fit as a first-order surrogate (mean preserved to ~0.03 %) suitable for E[response]-driven analysis; for distributional equivalence a comb-aware family or empirical-CDF inverse-transform sampler would be required (out of scope here).");
            System.out.println("Suggested diagnostics: (a) compare --itl-family={gamma, shifted_lognormal, lognormal_mixture} K-S statistics for the magnitude of the rejection, (b) inspect the empirical histogram (RESULTS.md §5.2.1 has the bins).");
        } else {
            System.out.println("Suggested diagnostics: (a) compare --itl-family options to find the lowest K-S statistic, (b) inspect the empirical histogram (RESULTS.md §5.2.1) — a comb-aware sampler may be needed for full distribution match.");
        }
        return 1;
    }

    private static double clampPBurst(double v) {
        if (Double.isNaN(v) || v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private double[] extractSamples(JsonNode arrayNode) {
        if (!arrayNode.isArray()) return new double[0];
        double[] xs = new double[arrayNode.size()];
        int i = 0;
        for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
            xs[i++] = it.next().asDouble();
        }
        return xs;
    }

    private double[] drawLogNormal(double mu, double sigma, int n) {
        LogNormalDistribution dist = new LogNormalDistribution(mu, sigma);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) xs[i] = dist.sample();
        return xs;
    }

    private double[] drawGamma(double shape, double scale, int n) {
        GammaDistribution dist = new GammaDistribution(shape, scale);
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) xs[i] = dist.sample();
        return xs;
    }

    private double[] drawZeroInflatedGamma(double shape, double scale, double pBurst, int n) {
        if (pBurst <= 0.0) return drawGamma(shape, scale, n);
        GammaDistribution dist = new GammaDistribution(shape, scale);
        java.util.Random rng = new java.util.Random();
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (rng.nextDouble() < pBurst) ? 0.0 : dist.sample();
        }
        return xs;
    }

    /**
     * Draw zero-inflated samples from a 3-parameter shifted log-normal
     * X = c + LogNormal(mu, sigma). With probability {@code pBurst} we emit
     * a zero-delay token (intra-burst arrival), otherwise we sample from
     * c + LN(mu, sigma). Mirrors the gamma draw method's zero-inflation
     * semantics so K-S comparison is apples-to-apples between the two
     * candidate families.
     */
    private double[] drawZeroInflatedShiftedLogNormal(double c, double mu, double sigma, double pBurst, int n) {
        LogNormalDistribution lognorm = new LogNormalDistribution(mu, sigma);
        java.util.Random rng = new java.util.Random();
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            if (pBurst > 0.0 && rng.nextDouble() < pBurst) {
                xs[i] = 0.0;
            } else {
                xs[i] = c + lognorm.sample();
            }
        }
        return xs;
    }

    /**
     * Draw zero-inflated samples from a 2-component log-normal mixture
     * {@code X ~ pi * LogNormal(mu1, sigma1) + (1 - pi) * LogNormal(mu2, sigma2)}.
     * Mirrors the gamma draw method's zero-inflation semantics.
     */
    private double[] drawZeroInflatedLogNormalMixture(
            double pi, double mu1, double sigma1, double mu2, double sigma2, double pBurst, int n) {
        LogNormalDistribution d1 = new LogNormalDistribution(mu1, sigma1);
        LogNormalDistribution d2 = new LogNormalDistribution(mu2, sigma2);
        java.util.Random rng = new java.util.Random();
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) {
            if (pBurst > 0.0 && rng.nextDouble() < pBurst) {
                xs[i] = 0.0;
            } else {
                xs[i] = (rng.nextDouble() < pi) ? d1.sample() : d2.sample();
            }
        }
        return xs;
    }
}
