package ro.tweebyte.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "itl-diagnostics",
        description = "Emit ITL histogram/quantile diagnostics from ai-stream-summarize calibration.json.")
public class ItlDiagnosticsCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--calibration-json", required = true)
    Path calibrationJson;

    @CommandLine.Option(names = "--out-dir", required = true)
    Path outDir;

    @CommandLine.Option(names = "--bins", defaultValue = "80")
    int bins;

    @CommandLine.Option(names = "--histogram-max-ms", defaultValue = "0",
            description = "Optional right edge for the histogram. 0 uses the observed max.")
    double histogramMaxMs;

    @CommandLine.Option(names = "--dpi", defaultValue = "300")
    int dpi;

    @CommandLine.Option(names = "--width", defaultValue = "1600")
    int width;

    @CommandLine.Option(names = "--height", defaultValue = "900")
    int height;

    @Override
    public Integer call() throws Exception {
        if (bins <= 0) {
            throw new IllegalArgumentException("--bins must be positive");
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(calibrationJson.toFile());
        JsonNode sampleNode = root.path("itl_samples");
        if (!sampleNode.isArray() || sampleNode.isEmpty()) {
            throw new IllegalArgumentException("calibration JSON has no itl_samples array: " + calibrationJson);
        }

        List<Double> samples = new ArrayList<>(sampleNode.size());
        for (JsonNode n : sampleNode) {
            samples.add(n.asDouble());
        }
        samples.sort(Comparator.naturalOrder());
        Files.createDirectories(outDir);

        writeSummary(root, samples);
        writeQuantiles(samples);
        HistogramData histogram = writeHistogram(samples);
        plotHistogram(histogram);

        System.out.printf(Locale.ROOT,
                "ITL diagnostics: n=%d mean=%.3fms p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms -> %s%n",
                samples.size(), mean(samples), quantile(samples, 0.50), quantile(samples, 0.95),
                quantile(samples, 0.99), samples.get(samples.size() - 1), outDir.toAbsolutePath());
        return 0;
    }

    private void writeSummary(JsonNode root, List<Double> samples) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(outDir.resolve("itl_summary.csv"))) {
            w.write("metric,value");
            w.newLine();
            w.write("n," + samples.size());
            w.newLine();
            w.write("mean_ms," + fmt(mean(samples)));
            w.newLine();
            w.write("median_ms," + fmt(quantile(samples, 0.50)));
            w.newLine();
            w.write("p95_ms," + fmt(quantile(samples, 0.95)));
            w.newLine();
            w.write("p99_ms," + fmt(quantile(samples, 0.99)));
            w.newLine();
            w.write("max_ms," + fmt(samples.get(samples.size() - 1)));
            w.newLine();

            JsonNode fits = root.path("itl_fits");
            if (!fits.isMissingNode()) {
                writeJsonField(w, fits, "p_burst");
                writeJsonField(w, fits, "burst_threshold_ms");
                writeJsonField(w, fits, "burst_count");
                writeJsonField(w, fits, "gap_n");
                writeJsonField(w, fits, "gap_mean");
                writeJsonField(w, fits, "gap_median");
            }
        }
    }

    private void writeJsonField(BufferedWriter w, JsonNode node, String name) throws Exception {
        if (node.has(name)) {
            w.write(name + "," + node.get(name).asText());
            w.newLine();
        }
    }

    private void writeQuantiles(List<Double> samples) throws Exception {
        double[] ps = new double[]{0.0, 0.001, 0.01, 0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95, 0.99, 0.999, 1.0};
        try (BufferedWriter w = Files.newBufferedWriter(outDir.resolve("itl_quantiles.csv"))) {
            w.write("p,itl_ms");
            w.newLine();
            for (double p : ps) {
                w.write(fmt(p) + "," + fmt(quantile(samples, p)));
                w.newLine();
            }
        }
    }

    private HistogramData writeHistogram(List<Double> samples) throws Exception {
        double max = histogramMaxMs > 0 ? histogramMaxMs : samples.get(samples.size() - 1);
        double min = 0.0;
        double widthMs = (max - min) / bins;
        if (widthMs <= 0) {
            throw new IllegalArgumentException("histogram range is empty; check --histogram-max-ms");
        }

        long[] counts = new long[bins];
        long included = 0;
        for (double x : samples) {
            if (x < min || x > max) continue;
            int idx = Math.min(bins - 1, (int) ((x - min) / widthMs));
            counts[idx]++;
            included++;
        }

        double[] centers = new double[bins];
        double[] density = new double[bins];
        try (BufferedWriter w = Files.newBufferedWriter(outDir.resolve("itl_histogram.csv"))) {
            w.write("bin_low_ms,bin_high_ms,count,density");
            w.newLine();
            for (int i = 0; i < bins; i++) {
                double low = min + i * widthMs;
                double high = low + widthMs;
                centers[i] = low + widthMs / 2.0;
                density[i] = included > 0 ? counts[i] / (included * widthMs) : 0.0;
                w.write(fmt(low) + "," + fmt(high) + "," + counts[i] + "," + fmt(density[i]));
                w.newLine();
            }
        }
        return new HistogramData(centers, density);
    }

    private void plotHistogram(HistogramData histogram) throws Exception {
        XYChart chart = new XYChartBuilder()
                .width(width)
                .height(height)
                .title("Inter-token latency distribution")
                .xAxisTitle("ITL (ms)")
                .yAxisTitle("density")
                .build();
        chart.addSeries("observed ITL", histogram.centers(), histogram.density());
        BitmapEncoder.saveBitmapWithDPI(chart,
                outDir.resolve("itl_histogram").toString(),
                BitmapEncoder.BitmapFormat.PNG, dpi);
    }

    private double quantile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return Double.NaN;
        if (p <= 0) return sorted.get(0);
        if (p >= 1) return sorted.get(sorted.size() - 1);
        double pos = p * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted.get(lo);
        double frac = pos - lo;
        return sorted.get(lo) * (1.0 - frac) + sorted.get(hi) * frac;
    }

    private double mean(List<Double> xs) {
        double sum = 0.0;
        for (double x : xs) sum += x;
        return sum / xs.size();
    }

    private String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private record HistogramData(double[] centers, double[] density) {
    }
}
