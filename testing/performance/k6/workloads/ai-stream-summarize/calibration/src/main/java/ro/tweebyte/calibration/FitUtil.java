package ro.tweebyte.calibration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.apache.commons.math3.distribution.WeibullDistribution;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.List;

/**
 * Distribution-fitting utilities shared between {@link CollectSamplesCommand}
 * (real-time fit during sample collection) and {@link RefitCommand} (re-fit
 * an existing calibration.json without re-collecting samples).
 *
 * <p>{@link #ITL_BURST_THRESHOLD_MS} (0.1 ms) is the cutoff below which an
 * inter-token interval is treated as a TCP/SSE burst-coalescing artifact
 * rather than a real model emission gap. ITLs below this floor are excluded
 * from the gap-mode fit but contribute to {@code p_burst} so {@code MockStreamingChatModel}
 * can reproduce the bimodal arrival pattern via zero inflation while preserving
 * the empirical {@code E[response]}.
 */
public final class FitUtil {

    public static final double ITL_BURST_THRESHOLD_MS = 0.1;

    private FitUtil() {}

    /**
     * @param burstThreshold {@code 0.0} disables burst-mode handling (TTFT path).
     *                       Any positive value records {@code p_burst} as the
     *                       fraction of strictly-positive samples below the
     *                       threshold and fits log-normal/gamma/Weibull only
     *                       to samples at or above the threshold.
     */
    public static ObjectNode fitAll(List<Double> raw, ObjectMapper mapper, double burstThreshold) {
        ObjectNode fits = mapper.createObjectNode();
        if (raw == null || raw.isEmpty()) return fits;

        double[] positive = raw.stream().mapToDouble(Double::doubleValue).filter(d -> d > 0).toArray();
        // Always emit n/mean/percentiles over the FULL positive set so the
        // empirical summary statistics (which downstream code uses to compute
        // E[response]) describe what was actually observed.
        fits.put("n", positive.length);
        fits.put("mean", Arrays.stream(positive).average().orElse(Double.NaN));
        fits.put("median", percentile(positive, 0.5));
        fits.put("p95", percentile(positive, 0.95));
        fits.put("p99", percentile(positive, 0.99));
        fits.put("p999", percentile(positive, 0.999));
        fits.put("max", Arrays.stream(positive).max().orElse(Double.NaN));

        double[] forFit;
        if (burstThreshold > 0.0) {
            long burstCount = Arrays.stream(positive).filter(v -> v < burstThreshold).count();
            double pBurst = positive.length == 0 ? 0.0 : (double) burstCount / positive.length;
            fits.put("p_burst", pBurst);
            fits.put("burst_threshold_ms", burstThreshold);
            fits.put("burst_count", burstCount);
            forFit = Arrays.stream(positive).filter(v -> v >= burstThreshold).toArray();
            fits.put("gap_n", forFit.length);
            if (forFit.length > 0) {
                fits.put("gap_mean", Arrays.stream(forFit).average().orElse(Double.NaN));
                fits.put("gap_median", percentile(forFit, 0.5));
            }
        } else {
            forFit = positive;
        }

        if (forFit.length == 0) {
            return fits;
        }

        // Log-normal: fit via log-transformed mean/stdev over the gap-mode subset.
        double[] logs = Arrays.stream(forFit).map(Math::log).toArray();
        double muLog = Arrays.stream(logs).average().orElse(Double.NaN);
        double sigmaLog = Math.sqrt(Arrays.stream(logs).map(l -> Math.pow(l - muLog, 2)).average().orElse(Double.NaN));
        LogNormalDistribution lognorm = new LogNormalDistribution(muLog, Math.max(1e-6, sigmaLog));
        double lognormLL = Arrays.stream(forFit).map(lognorm::logDensity).sum();
        ObjectNode lognormNode = mapper.createObjectNode();
        lognormNode.put("mu", muLog);
        lognormNode.put("sigma", sigmaLog);
        lognormNode.put("logL", lognormLL);
        lognormNode.put("aic", 2 * 2 - 2 * lognormLL);
        lognormNode.put("bic", Math.log(forFit.length) * 2 - 2 * lognormLL);
        fits.set("lognormal", lognormNode);

        // Gamma: method-of-moments (shape=mean^2/var, scale=var/mean).
        double mean = Arrays.stream(forFit).average().orElse(Double.NaN);
        double var = Arrays.stream(forFit).map(v -> Math.pow(v - mean, 2)).average().orElse(Double.NaN);
        double shape = (var > 0) ? (mean * mean) / var : 1.0;
        double scale = (mean > 0) ? var / mean : 1.0;
        try {
            GammaDistribution gamma = new GammaDistribution(shape, scale);
            double gammaLL = Arrays.stream(forFit).map(gamma::logDensity).sum();
            ObjectNode gammaNode = mapper.createObjectNode();
            gammaNode.put("shape", shape);
            gammaNode.put("scale", scale);
            gammaNode.put("logL", gammaLL);
            gammaNode.put("aic", 2 * 2 - 2 * gammaLL);
            gammaNode.put("bic", Math.log(forFit.length) * 2 - 2 * gammaLL);
            fits.set("gamma", gammaNode);
        } catch (Exception e) {
            fits.put("gamma_error", e.getMessage());
        }

        // Weibull: method-of-moments via approximate inversion (rough; fine for AIC comparison).
        try {
            double k = 1.2;
            double lambda = mean / Gamma.gamma(1 + 1.0 / k);
            WeibullDistribution weibull = new WeibullDistribution(k, lambda);
            double weibullLL = Arrays.stream(forFit).map(weibull::logDensity).sum();
            ObjectNode weibullNode = mapper.createObjectNode();
            weibullNode.put("shape", k);
            weibullNode.put("scale", lambda);
            weibullNode.put("logL", weibullLL);
            weibullNode.put("aic", 2 * 2 - 2 * weibullLL);
            weibullNode.put("bic", Math.log(forFit.length) * 2 - 2 * weibullLL);
            fits.set("weibull", weibullNode);
        } catch (Exception e) {
            fits.put("weibull_error", e.getMessage());
        }

        // 2-component log-normal mixture, fit via EM.
        // Motivation: the empirical ITL histogram for Qwen3.5-4B-MLX shows a
        // multimodal / comb-like structure (a tight cluster near 8.9 ms plus
        // a broader cluster near 21 ms, consistent with hardware token-clock
        // quantization on Apple Silicon Metal). No 2-parameter continuous
        // family can capture this; a 2-component mixture reduces the K-S
        // statistic substantially. This is a calibration-side analysis only;
        // the production mock binary still uses the gamma fit.
        try {
            ObjectNode mixtureNode = fitTwoComponentLogNormalMixture(forFit, mapper);
            if (mixtureNode != null) fits.set("lognormal_mixture_2", mixtureNode);
        } catch (Exception e) {
            fits.put("lognormal_mixture_2_error", e.getMessage());
        }

        // Shifted log-normal (3-parameter): X ~ c + LogNormal(mu, sigma).
        // Motivation: the empirical ITL gap-mode subset has a hard floor (~7.7 ms
        // for Qwen3.5-4B-MLX) that no 2-parameter continuous family with support
        // (0, infinity) can reproduce. K-S therefore rejects the 2-parameter gamma
        // / log-normal / Weibull fits even when their first two moments match the
        // empirical mean to <0.1%. A 3-parameter shifted family with location c
        // shifts the support to (c, infinity); when c is near the empirical floor
        // the K-S statistic drops dramatically.
        //
        // MLE strategy: for any fixed location c < min(forFit), the conditional
        // MLE for (mu, sigma) is the closed-form log-mean / log-stdev of (x - c).
        // This collapses the 3-parameter problem to a 1-D optimization over c,
        // which we solve with a Brent optimizer over [0, min(x) - epsilon].
        // Reported logL/AIC/BIC use 3 parameters so cross-family comparison stays
        // honest (3-parameter penalty is correctly applied).
        try {
            ObjectNode shiftedNode = fitShiftedLogNormal(forFit, mapper);
            if (shiftedNode != null) fits.set("shifted_lognormal", shiftedNode);
        } catch (Exception e) {
            fits.put("shifted_lognormal_error", e.getMessage());
        }

        return fits;
    }

    /**
     * Fit a 3-parameter shifted log-normal {@code X ~ c + LogNormal(mu, sigma)}
     * to {@code forFit} via MLE. Returns null if the input is degenerate
     * (n<3 or non-positive minimum).
     *
     * <p>The optimization is over the location parameter {@code c} only; for
     * each candidate {@code c}, the (mu, sigma) MLEs are the closed-form
     * log-mean / log-stdev of {@code (x - c)}. The conditional log-likelihood
     * collapses to {@code -sum log(x - c) - n*(log(sigma) + 0.5 log(2pi) + 0.5)}
     * because the closed-form MLE makes the squared-deviation term equal to
     * {@code n * sigma^2}.
     */
    static ObjectNode fitShiftedLogNormal(double[] forFit, ObjectMapper mapper) {
        if (forFit == null || forFit.length < 3) return null;
        double minX = Arrays.stream(forFit).min().orElse(Double.NaN);
        if (Double.isNaN(minX) || minX <= 0) return null;

        // Search interval: c ∈ [0, minX - epsilon]. Epsilon avoids log(0).
        double upper = Math.max(0.0, minX - Math.max(1e-9, minX * 1e-6));
        if (upper <= 0.0) return null;

        UnivariateFunction negLL = c -> {
            double[] logs = new double[forFit.length];
            double sumLog = 0.0;
            for (int i = 0; i < forFit.length; i++) {
                double diff = forFit[i] - c;
                if (diff <= 0.0) return Double.POSITIVE_INFINITY;
                logs[i] = Math.log(diff);
                sumLog += logs[i];
            }
            double mu = sumLog / logs.length;
            double sumSq = 0.0;
            for (double l : logs) {
                double d = l - mu;
                sumSq += d * d;
            }
            double sigma2 = sumSq / logs.length;
            if (sigma2 <= 1e-18) return Double.POSITIVE_INFINITY;
            double sigma = Math.sqrt(sigma2);
            // log-likelihood: sum log f(x_i; c, mu, sigma) where
            //   f(x; c, mu, sigma) = LogNormal_pdf(x - c; mu, sigma)
            // = -log(x - c) - log(sigma) - 0.5 log(2pi) - (log(x - c) - mu)^2 / (2 sigma^2)
            // Summed over i, and using the closed-form (mu, sigma) MLE:
            //   sum (log(x - c) - mu)^2 = n * sigma^2 → last term becomes n/2.
            double n = forFit.length;
            double logL = -sumLog - n * Math.log(sigma) - n * 0.5 * Math.log(2 * Math.PI) - n * 0.5;
            return -logL;
        };

        BrentOptimizer brent = new BrentOptimizer(1e-8, 1e-10);
        UnivariatePointValuePair result;
        try {
            result = brent.optimize(
                    new MaxEval(500),
                    new UnivariateObjectiveFunction(negLL),
                    new SearchInterval(0.0, upper),
                    GoalType.MINIMIZE);
        } catch (Exception e) {
            return null;
        }

        double cBest = result.getPoint();
        double[] logsBest = new double[forFit.length];
        double sumLog = 0.0;
        for (int i = 0; i < forFit.length; i++) {
            logsBest[i] = Math.log(forFit[i] - cBest);
            sumLog += logsBest[i];
        }
        double muBest = sumLog / logsBest.length;
        double sumSq = 0.0;
        for (double l : logsBest) {
            double d = l - muBest;
            sumSq += d * d;
        }
        double sigmaBest = Math.sqrt(sumSq / logsBest.length);
        double logL = -result.getValue();

        // Theoretical mean of c + LN(mu, sigma): c + exp(mu + sigma^2 / 2)
        double meanShifted = cBest + Math.exp(muBest + sigmaBest * sigmaBest / 2.0);

        ObjectNode node = mapper.createObjectNode();
        node.put("c", cBest);
        node.put("mu", muBest);
        node.put("sigma", sigmaBest);
        node.put("logL", logL);
        // 3 parameters, hence AIC/BIC penalty of 2*3 / log(n)*3
        node.put("aic", 2 * 3 - 2 * logL);
        node.put("bic", Math.log(forFit.length) * 3 - 2 * logL);
        node.put("mean", meanShifted);
        return node;
    }

    /**
     * Fit a 2-component log-normal mixture
     * {@code X ~ pi * LogNormal(mu1, sigma1) + (1 - pi) * LogNormal(mu2, sigma2)}
     * via Expectation-Maximization.
     *
     * <p>Calibration-side analysis only; not consumed by the production mock.
     * Documents that the ITL gap-mode distribution is multimodal in the
     * Qwen3.5-4B-MLX-4bit case (a tight cluster around 8.9 ms plus a broader
     * cluster around 21 ms) and that a 5-parameter mixture captures the
     * structure better than any 2-parameter family while still being
     * rejected by K-S at large n due to the empirical comb structure.
     *
     * <p>EM convergence: log-likelihood-tolerance 1e-7, max 2000 iterations.
     * Initialization: split log-samples at the median.
     */
    static ObjectNode fitTwoComponentLogNormalMixture(double[] forFit, ObjectMapper mapper) {
        if (forFit == null || forFit.length < 10) return null;
        double minX = Arrays.stream(forFit).min().orElse(Double.NaN);
        if (Double.isNaN(minX) || minX <= 0) return null;

        int n = forFit.length;
        double[] logX = new double[n];
        for (int i = 0; i < n; i++) logX[i] = Math.log(forFit[i]);

        double[] sorted = logX.clone();
        Arrays.sort(sorted);
        double median = sorted[n / 2];
        double overallMean = 0.0;
        for (double v : logX) overallMean += v;
        overallMean /= n;
        double overallSq = 0.0;
        for (double v : logX) overallSq += (v - overallMean) * (v - overallMean);
        double overallStd = Math.sqrt(overallSq / n);

        // Init: two components on either side of the median.
        double mu1 = 0.0, mu2 = 0.0;
        int n1 = 0, n2 = 0;
        for (double v : logX) {
            if (v < median) { mu1 += v; n1++; } else { mu2 += v; n2++; }
        }
        mu1 = (n1 > 0) ? mu1 / n1 : overallMean - 0.5;
        mu2 = (n2 > 0) ? mu2 / n2 : overallMean + 0.5;
        double sigma1 = Math.max(1e-3, overallStd / 2.0);
        double sigma2 = Math.max(1e-3, overallStd / 2.0);
        double pi = 0.5;

        final int MAX_ITER = 2000;
        final double TOL = 1e-7;
        double prevLL = Double.NEGATIVE_INFINITY;
        int iter = 0;
        for (; iter < MAX_ITER; iter++) {
            // E-step: posterior responsibility gamma_i = pi * f1(x_i) / (pi * f1(x_i) + (1-pi) * f2(x_i))
            // Working in log-density space avoids underflow.
            LogNormalDistribution d1 = new LogNormalDistribution(mu1, Math.max(1e-9, sigma1));
            LogNormalDistribution d2 = new LogNormalDistribution(mu2, Math.max(1e-9, sigma2));
            double[] gamma = new double[n];
            double ll = 0.0;
            for (int i = 0; i < n; i++) {
                double logP1 = Math.log(Math.max(pi, 1e-300)) + d1.logDensity(forFit[i]);
                double logP2 = Math.log(Math.max(1.0 - pi, 1e-300)) + d2.logDensity(forFit[i]);
                double logSum = logSumExp(logP1, logP2);
                ll += logSum;
                gamma[i] = Math.exp(logP1 - logSum);
            }

            // M-step
            double N1 = 0.0;
            for (double g : gamma) N1 += g;
            double N2 = n - N1;
            if (N1 < 1.0 || N2 < 1.0) break;
            double newMu1 = 0.0, newMu2 = 0.0;
            for (int i = 0; i < n; i++) {
                newMu1 += gamma[i] * logX[i];
                newMu2 += (1.0 - gamma[i]) * logX[i];
            }
            newMu1 /= N1;
            newMu2 /= N2;
            double newSig1Sq = 0.0, newSig2Sq = 0.0;
            for (int i = 0; i < n; i++) {
                double d1d = logX[i] - newMu1;
                double d2d = logX[i] - newMu2;
                newSig1Sq += gamma[i] * d1d * d1d;
                newSig2Sq += (1.0 - gamma[i]) * d2d * d2d;
            }
            double newSigma1 = Math.sqrt(Math.max(newSig1Sq / N1, 1e-12));
            double newSigma2 = Math.sqrt(Math.max(newSig2Sq / N2, 1e-12));
            double newPi = N1 / n;

            mu1 = newMu1;
            mu2 = newMu2;
            sigma1 = newSigma1;
            sigma2 = newSigma2;
            pi = newPi;

            if (Math.abs(ll - prevLL) < TOL) {
                prevLL = ll;
                iter++;
                break;
            }
            prevLL = ll;
        }

        // Order components so that component 1 always has the lower mean (deterministic ordering).
        double mean1 = Math.exp(mu1 + sigma1 * sigma1 / 2.0);
        double mean2 = Math.exp(mu2 + sigma2 * sigma2 / 2.0);
        if (mean1 > mean2) {
            double tMu = mu1, tSigma = sigma1, tMean = mean1;
            mu1 = mu2; sigma1 = sigma2; mean1 = mean2;
            mu2 = tMu; sigma2 = tSigma; mean2 = tMean;
            pi = 1.0 - pi;
        }

        // Theoretical mixture mean
        double mixtureMean = pi * mean1 + (1.0 - pi) * mean2;

        ObjectNode node = mapper.createObjectNode();
        node.put("pi", pi);
        node.put("mu1", mu1);
        node.put("sigma1", sigma1);
        node.put("mu2", mu2);
        node.put("sigma2", sigma2);
        node.put("mean1", mean1);
        node.put("mean2", mean2);
        node.put("logL", prevLL);
        // 5 parameters: pi, mu1, sigma1, mu2, sigma2
        node.put("aic", 2 * 5 - 2 * prevLL);
        node.put("bic", Math.log(n) * 5 - 2 * prevLL);
        node.put("mean", mixtureMean);
        node.put("em_iters", iter);
        return node;
    }

    private static double logSumExp(double a, double b) {
        if (a == Double.NEGATIVE_INFINITY) return b;
        if (b == Double.NEGATIVE_INFINITY) return a;
        double m = Math.max(a, b);
        return m + Math.log(Math.exp(a - m) + Math.exp(b - m));
    }

    private static double percentile(double[] xs, double q) {
        if (xs.length == 0) return Double.NaN;
        double[] sorted = xs.clone();
        Arrays.sort(sorted);
        int idx = (int) Math.max(0, Math.min(sorted.length - 1, Math.floor(q * (sorted.length - 1))));
        return sorted[idx];
    }
}
