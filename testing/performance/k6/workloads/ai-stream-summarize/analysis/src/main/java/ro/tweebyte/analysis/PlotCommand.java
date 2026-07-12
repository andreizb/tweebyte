package ro.tweebyte.analysis;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "plot",
        description = "Emit PNG figures from the stats CSV produced by `report`.")
public class PlotCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--stats-csv", required = true)
    Path statsCsv;

    @CommandLine.Option(names = "--out-dir", required = true,
            description = "Directory for PNG outputs.")
    Path outDir;

    @CommandLine.Option(names = "--dpi", defaultValue = "300")
    int dpi;

    @CommandLine.Option(names = "--width", defaultValue = "1600")
    int width;

    @CommandLine.Option(names = "--height", defaultValue = "900")
    int height;

    @CommandLine.Option(names = "--filter-campaign",
            description = "Optional comma-separated list of campaign labels. When set, only cells whose "
                    + "campaign appears in the list are rendered. Use to produce single-campaign figures "
                    + "from a multi-campaign cells.csv without re-running report. The campaign field is "
                    + "still used in grouping keys, so a multi-campaign cells.csv without --filter-campaign "
                    + "yields one figure per (workload, transport, calibration_tag, campaign) combination.")
    String filterCampaign;

    @CommandLine.Option(names = "--expected-response-ms", defaultValue = "0",
            description = "Expected full-response residency in ms for the normalized H1 plot. Use for "
                    + "single-workload cells; for mixed cells prefer --expected-response-ms-by-workload.")
    double expectedResponseMs;

    @CommandLine.Option(names = "--expected-response-ms-by-workload",
            description = "Comma-separated expected residency map, e.g. W0=6000,W1=5460,W2=5480. "
                    + "Overrides --expected-response-ms for matching workloads.")
    String expectedResponseMsByWorkload;

    @Override
    public Integer call() throws Exception {
        List<CellStat> all = loadStats(statsCsv);
        java.util.Set<String> campaignFilter = filterCampaign == null || filterCampaign.isBlank()
                ? null : new java.util.HashSet<>(java.util.Arrays.asList(filterCampaign.split(",")));
        List<CellStat> cells;
        if (campaignFilter == null) {
            cells = all;
        } else {
            cells = new ArrayList<>();
            for (CellStat c : all) {
                String camp = c.campaign == null ? "" : c.campaign;
                if (campaignFilter.contains(camp)) cells.add(c);
            }
        }
        Files.createDirectories(outDir);
        System.out.printf("Loaded %d cells (%d after --filter-campaign=%s)%n",
                all.size(), cells.size(),
                filterCampaign == null ? "(unset)" : filterCampaign);

        plotConcurrencyScaling(cells);
        plotPoolSizeScaling(cells);
        plotH1ValidationScatter(cells);
        plotResidencyCollapseScatter(cells, parseExpectedResponseMap());

        System.out.printf("PNGs written to %s%n", outDir.toAbsolutePath());
        return 0;
    }

    /** Per workload + transport + calibration tag + campaign: line plot of
     *  target_rps vs e2e p99, one series per stack × pool_size × reject_policy.
     *
     *  <p>Campaign is part of the grouping key so operationally distinct
     *  campaign slices render as separate figures even when the rest of the
     *  cell-key dimensions overlap.
     */
    private void plotConcurrencyScaling(List<CellStat> cells) throws Exception {
        Map<String, List<CellStat>> bySubgroup = new LinkedHashMap<>();
        for (CellStat c : cells) {
            String calTag = c.calibrationTag == null || c.calibrationTag.isEmpty()
                    ? "untagged" : c.calibrationTag;
            String camp = c.campaign == null || c.campaign.isEmpty()
                    ? "uncampaigned" : c.campaign;
            String k = c.workload + "_" + c.transport + "_" + calTag + "_" + camp
                    + "_" + c.tokensPerResponse + "_" + c.promptVariant + "_" + c.aiBackend;
            bySubgroup.computeIfAbsent(k, x -> new ArrayList<>()).add(c);
        }
        for (var group : bySubgroup.entrySet()) {
            XYChart chart = new XYChartBuilder()
                    .width(width).height(height)
                    .title("p99 E2E vs target arrival rate — " + group.getKey())
                    .xAxisTitle("Target RPS").yAxisTitle("p99 E2E (ms)").build();
            chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
            chart.getStyler().setYAxisLogarithmic(true);

            Map<String, List<CellStat>> bySeries = new LinkedHashMap<>();
            for (CellStat c : group.getValue()) {
                StringBuilder s = new StringBuilder(c.stack);
                if (!c.poolSize.isEmpty()) s.append(" pool=").append(c.poolSize);
                if (!c.rejectPolicy.isEmpty()) s.append(" policy=").append(c.rejectPolicy);
                String series = s.toString();
                bySeries.computeIfAbsent(series, x -> new ArrayList<>()).add(c);
            }
            for (var s : bySeries.entrySet()) {
                // Drop cells with NaN p99 (all runs quarantined): a log-scale Y
                // axis can't render zeros, and a placeholder zero would distort
                // the line plot anyway. The cells.csv still records them in the
                // quarantined_* columns; the figure just skips the points.
                List<CellStat> pts = new ArrayList<>(s.getValue());
                pts.removeIf(p -> Double.isNaN(p.e2eP99Mean));
                pts.sort(Comparator.comparingInt(c -> c.targetRps));
                if (pts.isEmpty()) continue;
                double[] xs = pts.stream().mapToDouble(p -> p.targetRps).toArray();
                double[] ys = pts.stream().mapToDouble(p -> p.e2eP99Mean).toArray();
                double[] err = pts.stream().mapToDouble(p ->
                        (Double.isNaN(p.e2eP99CiHigh) || Double.isNaN(p.e2eP99CiLow))
                                ? 0 : (p.e2eP99CiHigh - p.e2eP99CiLow) / 2.0).toArray();
                chart.addSeries(s.getKey(), xs, ys, err);
            }
            BitmapEncoder.saveBitmapWithDPI(chart,
                    outDir.resolve("target_rps_scaling_" + safe(group.getKey())).toString(),
                    BitmapEncoder.BitmapFormat.PNG, dpi);
        }
    }

    /** For async only: pool_size vs p99 at fixed target_rps, one series per
     *  (target_rps, calibration_tag, campaign). Campaign in the grouping key
     *  matches plotConcurrencyScaling so the same campaign-aware split applies
     *  here too — campaign-scoped cells render as separate figures even
     *  when the rest of the cell-key dimensions overlap.
     */
    private void plotPoolSizeScaling(List<CellStat> cells) throws Exception {
        List<CellStat> asyncCells = cells.stream().filter(c -> "async".equals(c.stack)).toList();
        Map<String, List<CellStat>> byWorkload = new LinkedHashMap<>();
        for (CellStat c : asyncCells) {
            String calTag = c.calibrationTag == null || c.calibrationTag.isEmpty()
                    ? "untagged" : c.calibrationTag;
            String camp = c.campaign == null || c.campaign.isEmpty()
                    ? "uncampaigned" : c.campaign;
            byWorkload.computeIfAbsent(c.workload + "_" + c.transport + "_" + calTag + "_" + camp
                            + "_" + c.tokensPerResponse + "_" + c.promptVariant + "_" + c.aiBackend,
                    x -> new ArrayList<>()).add(c);
        }
        for (var group : byWorkload.entrySet()) {
            XYChart chart = new XYChartBuilder()
                    .width(width).height(height)
                    .title("p99 E2E vs pool size (async) — " + group.getKey())
                    .xAxisTitle("Thread-pool size").yAxisTitle("p99 E2E (ms)").build();
            chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
            chart.getStyler().setYAxisLogarithmic(true);

            Map<Integer, List<CellStat>> byRps = new LinkedHashMap<>();
            for (CellStat c : group.getValue()) byRps.computeIfAbsent(c.targetRps, x -> new ArrayList<>()).add(c);
            byRps.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(rpsEntry -> {
                List<CellStat> pts = new ArrayList<>(rpsEntry.getValue());
                pts.removeIf(p -> parsePoolSize(p.poolSize) <= 0 || Double.isNaN(p.e2eP99Mean));
                pts.sort(Comparator.comparingInt(p -> parsePoolSize(p.poolSize)));
                if (pts.isEmpty()) return;
                double[] xs = pts.stream().mapToDouble(p -> parsePoolSize(p.poolSize)).toArray();
                double[] ys = pts.stream().mapToDouble(p -> p.e2eP99Mean).toArray();
                chart.addSeries("rps=" + rpsEntry.getKey(), xs, ys);
            });
            BitmapEncoder.saveBitmapWithDPI(chart,
                    outDir.resolve("pool_size_scaling_" + safe(group.getKey())).toString(),
                    BitmapEncoder.BitmapFormat.PNG, dpi);
        }
    }

    /**
     * Scatter: for each matched async-reactive cell pair (matched on
     * workload, transport, target_rps, cancel_rate, calibration_tag, campaign),
     * x = arrival-rate / pool size (open-loop λ/T), y = p99_async / p99_reactive.
     *
     * <p>Both calibration_tag and campaign are part of the matching key,
     * so campaign-scoped cells do not pair with other campaign slices that
     * share the same load shape, and calibrated and uncalibrated cells do
     * not pair across batches. The cell-key dimensions on disk in cells.csv
     * are mirrored here.
     */
    private void plotH1ValidationScatter(List<CellStat> cells) throws Exception {
        Map<String, List<CellStat>> asyncByShared = new LinkedHashMap<>();
        Map<String, CellStat> reactiveByShared = new LinkedHashMap<>();
        for (CellStat c : cells) {
            String calTag = c.calibrationTag == null ? "" : c.calibrationTag;
            String camp = c.campaign == null ? "" : c.campaign;
            // Include the three AI-streaming axes so mock/live and short/long cells never pair.
            String tpr = c.tokensPerResponse == null ? "" : c.tokensPerResponse;
            String pv = c.promptVariant == null ? "" : c.promptVariant;
            String ab = c.aiBackend == null ? "" : c.aiBackend;
            String common = String.join("|", c.workload, c.transport,
                    String.valueOf(c.targetRps), String.valueOf(c.cancelRate), calTag, camp,
                    tpr, pv, ab);
            if ("async".equals(c.stack)) {
                asyncByShared.computeIfAbsent(common, k -> new ArrayList<>()).add(c);
            } else if ("reactive".equals(c.stack)) {
                reactiveByShared.put(common, c);
            }
        }
        List<Double> ratio = new ArrayList<>();
        List<Double> p99Ratio = new ArrayList<>();
        for (var entry : asyncByShared.entrySet()) {
            CellStat r = reactiveByShared.get(entry.getKey());
            if (r == null || Double.isNaN(r.e2eP99Mean) || r.e2eP99Mean == 0) continue;
            for (CellStat a : entry.getValue()) {
                int poolSize = parsePoolSize(a.poolSize);
                if (poolSize <= 0 || Double.isNaN(a.e2eP99Mean)) continue;
                ratio.add((double) a.targetRps / poolSize);
                p99Ratio.add(a.e2eP99Mean / r.e2eP99Mean);
            }
        }
        if (ratio.isEmpty()) {
            System.out.println("H1 scatter: no async/reactive paired cells with pool_size yet; skipping.");
            return;
        }
        XYChart chart = new XYChartBuilder()
                .width(width).height(height)
                .title("H1 validation: p99 ratio (async/reactive) vs arrival-rate per thread")
                .xAxisTitle("Target arrival rate / pool size (λ/T, open-loop)")
                .yAxisTitle("p99 async / p99 reactive").build();
        chart.getStyler().setDefaultSeriesRenderStyle(org.knowm.xchart.XYSeries.XYSeriesRenderStyle.Scatter);
        chart.getStyler().setYAxisLogarithmic(true);
        double[] xs = ratio.stream().mapToDouble(Double::doubleValue).toArray();
        double[] ys = p99Ratio.stream().mapToDouble(Double::doubleValue).toArray();
        chart.addSeries("cells", xs, ys);
        BitmapEncoder.saveBitmapWithDPI(chart,
                outDir.resolve("h1_validation_scatter").toString(),
                BitmapEncoder.BitmapFormat.PNG, dpi);
    }

    /**
     * Same async/reactive pairing as plotH1ValidationScatter, but the x-axis is
     * normalized open-loop pressure:
     *
     * <pre>
     * target_rps * expected_response_seconds / async_pool_size
     * </pre>
     *
     * Values near 1 mean Little's-law occupancy approaches the configured
     * platform-thread pool size. This is the normalized threshold-fill shape;
     * sparse raw-RPS line plots are only exploratory.
     */
    private void plotResidencyCollapseScatter(List<CellStat> cells, Map<String, Double> expectedMsByWorkload)
            throws Exception {
        if (expectedResponseMs <= 0 && expectedMsByWorkload.isEmpty()) {
            return;
        }
        buildCollapse(cells, expectedMsByWorkload, false);
        // Companion plot keyed by delivered (SUT-faced) RPS, emitted when the report
        // carried delivered_rps. The target-RPS x-axis is conservative for cells with a
        // non-zero dropped_rate: k6 open-loop VU-starvation drops are load-generator
        // side, not SUT rejection, so the SUT faced fewer arrivals and still cliffed.
        // The delivered-RPS plot places each point at the rate the SUT actually faced.
        boolean anyDelivered = cells.stream().anyMatch(c -> !Double.isNaN(c.deliveredRps) && c.deliveredRps > 0);
        if (anyDelivered) {
            buildCollapse(cells, expectedMsByWorkload, true);
        }
    }

    private void buildCollapse(List<CellStat> cells, Map<String, Double> expectedMsByWorkload, boolean useDelivered)
            throws Exception {
        Map<String, List<CellStat>> asyncByShared = new LinkedHashMap<>();
        Map<String, CellStat> reactiveByShared = new LinkedHashMap<>();
        for (CellStat c : cells) {
            String calTag = c.calibrationTag == null ? "" : c.calibrationTag;
            String camp = c.campaign == null ? "" : c.campaign;
            // Include the three AI-streaming axes so mock/live and short/long cells never pair.
            String tpr = c.tokensPerResponse == null ? "" : c.tokensPerResponse;
            String pv = c.promptVariant == null ? "" : c.promptVariant;
            String ab = c.aiBackend == null ? "" : c.aiBackend;
            String common = String.join("|", c.workload, c.transport,
                    String.valueOf(c.targetRps), String.valueOf(c.cancelRate), calTag, camp,
                    tpr, pv, ab);
            if ("async".equals(c.stack)) {
                asyncByShared.computeIfAbsent(common, k -> new ArrayList<>()).add(c);
            } else if ("reactive".equals(c.stack)) {
                reactiveByShared.put(common, c);
            }
        }

        List<Double> pressure = new ArrayList<>();
        List<Double> p99Ratio = new ArrayList<>();
        for (var entry : asyncByShared.entrySet()) {
            CellStat r = reactiveByShared.get(entry.getKey());
            if (r == null || Double.isNaN(r.e2eP99Mean) || r.e2eP99Mean == 0) continue;
            for (CellStat a : entry.getValue()) {
                int poolSize = parsePoolSize(a.poolSize);
                double expectedMs = expectedMsByWorkload.getOrDefault(a.workload, expectedResponseMs);
                if (poolSize <= 0 || expectedMs <= 0 || Double.isNaN(a.e2eP99Mean)) continue;
                double xRps = useDelivered ? a.deliveredRps : a.targetRps;
                if (Double.isNaN(xRps) || xRps <= 0) continue;
                pressure.add(xRps * (expectedMs / 1000.0) / poolSize);
                p99Ratio.add(a.e2eP99Mean / r.e2eP99Mean);
            }
        }

        if (pressure.isEmpty()) {
            if (!useDelivered) {
                System.out.println("Residency-collapse scatter: no paired cells with expected response time; skipping.");
            }
            return;
        }

        double yMin = p99Ratio.stream().mapToDouble(Double::doubleValue).min().orElse(1.0);
        double yMax = p99Ratio.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        yMin = Math.max(0.1, yMin * 0.8);
        yMax = Math.max(yMin * 1.2, yMax * 1.2);

        String rpsLabel = useDelivered ? "delivered" : "target";
        XYChart chart = new XYChartBuilder()
                .width(width).height(height)
                .title("H1 validation: p99 ratio vs normalized arrival pressure (" + rpsLabel + " RPS)")
                .xAxisTitle(rpsLabel + " RPS × E[response seconds] / pool size")
                .yAxisTitle("p99 async / p99 reactive").build();
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart.getStyler().setYAxisLogarithmic(true);
        chart.addSeries("cells",
                pressure.stream().mapToDouble(Double::doubleValue).toArray(),
                p99Ratio.stream().mapToDouble(Double::doubleValue).toArray());
        XYSeries threshold = chart.addSeries("pool-residency threshold",
                new double[]{1.0, 1.0}, new double[]{yMin, yMax});
        threshold.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        BitmapEncoder.saveBitmapWithDPI(chart,
                outDir.resolve(useDelivered ? "h1_residency_collapse_delivered" : "h1_residency_collapse_scatter").toString(),
                BitmapEncoder.BitmapFormat.PNG, dpi);
    }

    static int parsePoolSize(String tag) {
        if (tag == null) return 0;
        String digits = tag.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            long n = Long.parseLong(digits);
            return (n <= 0 || n > Integer.MAX_VALUE) ? 0 : (int) n;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private Map<String, Double> parseExpectedResponseMap() {
        Map<String, Double> out = new HashMap<>();
        if (expectedResponseMsByWorkload == null || expectedResponseMsByWorkload.isBlank()) {
            return out;
        }
        for (String part : expectedResponseMsByWorkload.split(",")) {
            if (part.isBlank()) continue;
            String[] pieces = part.split("=", 2);
            if (pieces.length != 2) {
                throw new IllegalArgumentException("Expected workload=ms in --expected-response-ms-by-workload, got: " + part);
            }
            out.put(pieces[0].trim(), Double.parseDouble(pieces[1].trim()));
        }
        return out;
    }

    private List<CellStat> loadStats(Path csv) throws java.io.IOException {
        List<CellStat> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String header = r.readLine();
            if (header == null) return out;
            // Four schemas:
            //   - Pre-2026-04-27: 23 cols, no calibration_tag, no quarantined_*
            //   - 2026-04-27: 27 cols, calibration_tag at index 7
            //   - 2026-04-28+: 28 cols, calibration_tag + campaign at indices 7,8
            //   - 2026-06-13+: same leading columns, plus dropped_rate/delivered_rps
            //     after total_dropped; this plotting reader ignores trailing columns.
            boolean hasCampaign = header.contains("campaign");
            boolean hasCal = hasCampaign || header.contains("calibration_tag");
            // delivered_rps lives in the trailing columns (2026-06-13+); resolve its
            // index from the header so the collapse plot can offer a delivered-RPS
            // x-axis without hard-coding a position.
            java.util.List<String> headerCols = java.util.Arrays.asList(header.split(",", -1));
            int deliveredIdx = headerCols.indexOf("delivered_rps");
            // AI-streaming axes live at the tail of cells.csv (2026-06-28+); resolve
            // them by header name so older cells.csv files without them still load.
            int tprIdx = headerCols.indexOf("tokens_per_response");
            int pvIdx = headerCols.indexOf("prompt_variant");
            int abIdx = headerCols.indexOf("ai_backend");
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",", -1);
                int idx = 0;
                String stack = f[idx++];
                String workload = f[idx++];
                String transport = f[idx++];
                int rps = Integer.parseInt(f[idx++]);
                String poolSize = f[idx++];
                String rejectPolicy = f[idx++];
                double cancelRate = Double.parseDouble(emptyTo(f[idx++], "0"));
                String calibrationTag = hasCal ? f[idx++] : "";
                String campaign = hasCampaign ? f[idx++] : "";
                int nRuns = Integer.parseInt(emptyTo(f[idx++], "0"));
                double e2eP99Mean = parseD(f[idx++]);
                double e2eP99CiLow = parseD(f[idx++]);
                double e2eP99CiHigh = parseD(f[idx++]);
                double ttftP99Mean = parseD(f[idx++]);
                double ttftP99CiLow = parseD(f[idx++]);
                double ttftP99CiHigh = parseD(f[idx++]);
                double deliveredRps = (deliveredIdx >= 0 && deliveredIdx < f.length) ? parseD(f[deliveredIdx]) : Double.NaN;
                String tokensPerResponse = (tprIdx >= 0 && tprIdx < f.length) ? f[tprIdx] : "";
                String promptVariant = (pvIdx >= 0 && pvIdx < f.length) ? f[pvIdx] : "";
                String aiBackend = (abIdx >= 0 && abIdx < f.length) ? f[abIdx] : "";
                out.add(new CellStat(stack, workload, transport, rps,
                        poolSize, rejectPolicy, cancelRate, calibrationTag, campaign, nRuns,
                        e2eP99Mean, e2eP99CiLow, e2eP99CiHigh,
                        ttftP99Mean, ttftP99CiLow, ttftP99CiHigh, deliveredRps,
                        tokensPerResponse, promptVariant, aiBackend));
            }
        }
        return out;
    }

    private double parseD(String s) {
        return (s == null || s.isEmpty()) ? Double.NaN : Double.parseDouble(s);
    }

    private String emptyTo(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    record CellStat(String stack, String workload, String transport, int targetRps,
                    String poolSize, String rejectPolicy, double cancelRate,
                    String calibrationTag, String campaign, int nRuns,
                    double e2eP99Mean, double e2eP99CiLow, double e2eP99CiHigh,
                    double ttftP99Mean, double ttftP99CiLow, double ttftP99CiHigh,
                    double deliveredRps,
                    String tokensPerResponse, String promptVariant, String aiBackend) {
    }
}
