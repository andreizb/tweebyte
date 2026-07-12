package ro.tweebyte.analysis;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.apache.commons.math3.stat.inference.TTest;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "report",
        description = "From a runs CSV, compute per-cell bootstrap CI on per-run p99 + headline paired tests; emit a stats CSV.")
public class ReportCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--runs-csv", required = true)
    Path runsCsv;

    @CommandLine.Option(names = "--out", required = true,
            description = "Output CSV of per-cell aggregated statistics (one row per cell).")
    Path outPath;

    @CommandLine.Option(names = "--bootstrap-samples", defaultValue = "10000")
    int bootstrapSamples;

    @CommandLine.Option(names = "--alpha", defaultValue = "0.05",
            description = "Significance level for t-test / Mann-Whitney U comparisons across paired cells.")
    double alpha;

    @CommandLine.Option(names = "--include-status",
            defaultValue = "OK,NO_VALIDATION_SIDECAR",
            description = "Comma-separated list of cell_status values to include in the report. Runs whose "
                    + "cell_status is not in this list are dropped from aggregates entirely (they still appear "
                    + "in runs.csv). Default keeps OK runs and runs without a validation sidecar "
                    + "(NO_VALIDATION_SIDECAR). Set to e.g. 'OK' for a strict OK-only export, or "
                    + "'OK,NO_VALIDATION_SIDECAR,CONTAMINATED' to include everything.")
    String includeStatus;

    @CommandLine.Option(names = "--filter-campaign",
            description = "Optional comma-separated list of campaign labels. When set, only runs whose "
                    + "campaign appears in the list contribute to the report. Use to produce a single-campaign "
                    + "cells.csv from a single campaign without changing the runs.csv schema.")
    String filterCampaign;

    @CommandLine.Option(names = "--duration-secs", defaultValue = "180",
            description = "Main measurement-window duration per run, in seconds. Used only for delivered_rps.")
    int durationSecs;

    @Override
    public Integer call() throws Exception {
        if (durationSecs <= 0) {
            throw new IllegalArgumentException("--duration-secs must be positive");
        }
        List<RunResult> allRuns = loadRuns(runsCsv);
        java.util.Set<String> statusSet = new java.util.HashSet<>(java.util.Arrays.asList(includeStatus.split(",")));
        java.util.Set<String> campaignFilter = filterCampaign == null || filterCampaign.isBlank()
                ? null : new java.util.HashSet<>(java.util.Arrays.asList(filterCampaign.split(",")));
        List<RunResult> runs = new ArrayList<>();
        long droppedByStatus = 0, droppedByCampaign = 0;
        for (RunResult r : allRuns) {
            if (!statusSet.contains(r.cellStatus())) { droppedByStatus++; continue; }
            if (campaignFilter != null && !campaignFilter.contains(r.campaign())) { droppedByCampaign++; continue; }
            runs.add(r);
        }
        System.out.printf("Loaded %d total runs; %d dropped by --include-status=%s; %d dropped by --filter-campaign=%s%n",
                allRuns.size(), droppedByStatus, includeStatus,
                droppedByCampaign, filterCampaign == null ? "(unset)" : filterCampaign);
        Map<String, List<RunResult>> byCell = new LinkedHashMap<>();
        for (RunResult r : runs) byCell.computeIfAbsent(r.cellKey(), k -> new ArrayList<>()).add(r);

        System.out.printf("Loaded %d runs across %d cells%n", runs.size(), byCell.size());
        Files.createDirectories(outPath.getParent() != null ? outPath.getParent() : Path.of("."));
        try (BufferedWriter w = Files.newBufferedWriter(outPath)) {
            // Per-cell summary now distinguishes:
            //   * "contributing" runs — at least one successful e2e sample, so e2e_p99 is non-null;
            //     these drive n_runs, e2e_p99_mean, ttft_p99_mean, error_rate, MW-U headline tests.
            //   * "quarantined" runs — zero successful e2e samples, e2e_p99 is null (k6 emitted
            //     no main-phase main-scenario successes within the run window); these are reported
            //     in separate quarantined_* columns so a cell that genuinely had a 100%-failure
            //     run still tracks the failure population without distorting the latency stats.
            // The quarantine threshold is exactly null e2e_p99 — runs with even a single
            // successful sample stay in the contributing pool.
            w.write("stack,workload,transport,target_rps,pool_size,reject_policy,cancel_rate,calibration_tag,"
                    + "campaign,"
                    + "n_runs,e2e_p99_mean,e2e_p99_ci_low,e2e_p99_ci_high,"
                    + "ttft_p99_mean,ttft_p99_ci_low,ttft_p99_ci_high,"
                    + "e2e_failed_p99_mean,e2e_failed_p99_ci_low,e2e_failed_p99_ci_high,"
                    + "total_failed_observations,"
                    + "total_requests,total_errors,total_dropped,dropped_rate,delivered_rps,error_rate,"
                    + "quarantined_n_runs,quarantined_requests,quarantined_errors,quarantined_failed_p99_mean,"
                    // Trailing columns: mean-of-per-run percentile spread, W0-residency
                    // metadata, and per-cell CPU/heap aggregated from each run's *_resources.csv.
                    // Appended at the tail so the schema-detecting plot reader (header-index lookups)
                    // is unaffected and older cells.csv files still load.
                    + "e2e_p50_mean,e2e_p95_mean,e2e_p999_mean,"
                    + "mock_tokens,mock_itl_ms,workload_variant,"
                    + "cpu_avg,cpu_p95,cpu_max,heap_used_avg_mb,heap_used_max_mb,"
                    + "heap_committed_avg_mb,heap_committed_max_mb,resource_samples,"
                    + "transport_error_total,transport_error_rate,app_5xx_total,app_5xx_rate,"
                    + "reject_total,app_reject_rate,publication_clean,"
                    // AI-streaming axes + derived metadata appended at the very tail
                    // so PlotCommand's positional cells.csv reader is unaffected.
                    + "tokens_per_response,prompt_variant,ai_backend,prompt_chars,prompt_approx_tokens");
            w.newLine();
            for (var e : byCell.entrySet()) {
                RunResult head = e.getValue().get(0);
                List<RunResult> contributing = e.getValue().stream()
                        .filter(r -> r.e2eP99() != null).toList();
                List<RunResult> quarantined = e.getValue().stream()
                        .filter(r -> r.e2eP99() == null).toList();

                double[] e2eP99s = contributing.stream().mapToDouble(RunResult::e2eP99).toArray();
                double[] ttftP99s = contributing.stream()
                        .filter(r -> r.ttftP99() != null).mapToDouble(RunResult::ttftP99).toArray();
                double[] e2eFailedP99s = contributing.stream()
                        .filter(r -> r.e2eFailedP99() != null).mapToDouble(RunResult::e2eFailedP99).toArray();

                long totalReq = contributing.stream().mapToLong(RunResult::requests).sum();
                long totalErr = contributing.stream().mapToLong(RunResult::errors).sum();
                long totalDropped = contributing.stream().mapToLong(RunResult::dropped).sum();
                long totalFailedObs = contributing.stream().mapToLong(RunResult::e2eFailedCount).sum();
                double errorRate = totalReq > 0 ? (double) totalErr / totalReq : Double.NaN;
                double droppedRate = (totalReq + totalDropped) > 0
                        ? (double) totalDropped / (totalReq + totalDropped) : Double.NaN;
                double deliveredRps = contributing.isEmpty() || durationSecs <= 0
                        ? Double.NaN : (double) totalReq / (contributing.size() * durationSecs);

                // Error CLASSIFICATION split — keep transport/admission collapse out of the
                // application-level rejection signal. transport_error_rate = dial-timeout +
                // request-timeout + addr-exhaustion + reset/eof/other (k6 status==0);
                // app_reject_rate = authoritative AbortPolicy reject_delta from Prometheus.
                // A cell whose failures are transport-dominated is not a clean H1 measurement.
                int transportErrTotal = contributing.stream().mapToInt(RunResult::transportErrors).sum();
                int app5xxTotal = contributing.stream().mapToInt(r -> r.errorsOfType("http_5xx")).sum();
                int rejectTotal = contributing.stream().mapToInt(r -> r.rejectDelta() == null ? 0 : r.rejectDelta()).sum();
                double transportErrRate = totalReq > 0 ? (double) transportErrTotal / totalReq : Double.NaN;
                double app5xxRate = totalReq > 0 ? (double) app5xxTotal / totalReq : Double.NaN;
                double appRejectRate = totalReq > 0 ? (double) rejectTotal / totalReq : Double.NaN;

                long qReq = quarantined.stream().mapToLong(RunResult::requests).sum();
                long qErr = quarantined.stream().mapToLong(RunResult::errors).sum();
                double[] qFailedP99s = quarantined.stream()
                        .filter(r -> r.e2eFailedP99() != null).mapToDouble(RunResult::e2eFailedP99).toArray();

                double[] e2eCi = bootstrapMeanCi(e2eP99s, bootstrapSamples, alpha);
                double[] ttftCi = bootstrapMeanCi(ttftP99s, bootstrapSamples, alpha);
                double[] e2eFailedCi = bootstrapMeanCi(e2eFailedP99s, bootstrapSamples, alpha);

                // Mean-of-per-run lower percentiles (tables/figures want p50/p95/p999, not just p99).
                double e2eP50Mean = mean(contributing.stream()
                        .filter(r -> r.e2eP50() != null).mapToDouble(RunResult::e2eP50).toArray());
                double e2eP95Mean = mean(contributing.stream()
                        .filter(r -> r.e2eP95() != null).mapToDouble(RunResult::e2eP95).toArray());
                double e2eP999Mean = mean(contributing.stream()
                        .filter(r -> r.e2eP999() != null).mapToDouble(RunResult::e2eP999).toArray());
                // Per-cell CPU/heap pooled from each contributing run's *_resources.csv.
                ResourceAgg res = aggregateResources(contributing);

                w.write(String.join(",",
                        head.stack(), head.workload(), head.transport(),
                        String.valueOf(head.targetRps()), head.poolSize(),
                        head.rejectPolicy(), String.valueOf(head.cancelRate()),
                        head.calibrationTag() == null ? "" : head.calibrationTag(),
                        head.campaign() == null ? "" : head.campaign(),
                        String.valueOf(contributing.size()),
                        fmt(mean(e2eP99s)), fmt(e2eCi[0]), fmt(e2eCi[1]),
                        fmt(mean(ttftP99s)), fmt(ttftCi[0]), fmt(ttftCi[1]),
                        fmt(mean(e2eFailedP99s)), fmt(e2eFailedCi[0]), fmt(e2eFailedCi[1]),
                        String.valueOf(totalFailedObs),
                        String.valueOf(totalReq), String.valueOf(totalErr),
                        String.valueOf(totalDropped), fmt(droppedRate), fmt(deliveredRps), fmt(errorRate),
                        String.valueOf(quarantined.size()),
                        String.valueOf(qReq), String.valueOf(qErr),
                        fmt(mean(qFailedP99s)),
                        fmt(e2eP50Mean), fmt(e2eP95Mean), fmt(e2eP999Mean),
                        intOrEmpty(head.mockTokens()), intOrEmpty(head.mockItlMs()),
                        head.workloadVariant() == null ? "" : head.workloadVariant(),
                        fmt(res.cpuAvg()), fmt(res.cpuP95()), fmt(res.cpuMax()),
                        fmt(res.heapAvgMb()), fmt(res.heapMaxMb()),
                        fmt(res.heapCommittedAvgMb()), fmt(res.heapCommittedMaxMb()),
                        String.valueOf(res.samples()),
                        String.valueOf(transportErrTotal), fmt(transportErrRate),
                        String.valueOf(app5xxTotal), fmt(app5xxRate),
                        String.valueOf(rejectTotal), fmt(appRejectRate),
                        // publication-clean ⇔ cell not quarantined AND transport contamination
                        // ≤ 0.1% (Codex GO bar). The cellStatus==OK guard means a TRANSPORT_LIMITED
                        // cell is never publication-clean even if the analysis-layer rate reads 0
                        // (e.g. pre-classification data with no errors_by_type). The 0.1%–0.5% band
                        // stays OK but is a transport-warning point, not headline-curve evidence.
                        ("OK".equals(head.cellStatus()) && !Double.isNaN(transportErrRate)
                                && transportErrRate <= 0.001) ? "1" : "0",
                        // AI-streaming axes + derived metadata from the cell's first run.
                        intOrEmpty(head.tokensPerResponse()),
                        head.promptVariant() == null ? "" : head.promptVariant(),
                        head.aiBackend() == null ? "" : head.aiBackend(),
                        intOrEmpty(head.promptChars()),
                        intOrEmpty(head.promptApproxTokens())));
                w.newLine();
            }
        }
        System.out.printf("Wrote %s (%d cells)%n", outPath.toAbsolutePath(), byCell.size());

        runHeadlineTests(byCell);
        return 0;
    }

    private void runHeadlineTests(Map<String, List<RunResult>> byCell) {
        // Pair every async cell against every reactive cell that shares the load shape
        // (workload, transport, target_rps, cancel_rate, calibration_tag, campaign).
        // Campaign is part of the matching key so campaign-scoped cells do not
        // pair with other campaign slices that share the same load shape.
        TTest tTest = new TTest();
        MannWhitneyUTest mw = new MannWhitneyUTest();

        Map<String, List<CellRuns>> asyncByShared = new LinkedHashMap<>();
        Map<String, List<CellRuns>> reactiveByShared = new LinkedHashMap<>();
        for (var e : byCell.entrySet()) {
            RunResult h = e.getValue().get(0);
            String tag = h.calibrationTag() == null ? "" : h.calibrationTag();
            String camp = h.campaign() == null ? "" : h.campaign();
            // Include the three AI-streaming axes so an async mock cell never pairs
            // with a reactive live cell, nor a short-prompt cell with a long-prompt one.
            String tpr = h.tokensPerResponse() == null ? "" : String.valueOf(h.tokensPerResponse());
            String pv = h.promptVariant() == null ? "" : h.promptVariant();
            String ab = h.aiBackend() == null ? "" : h.aiBackend();
            String sharedKey = String.join("|", h.workload(), h.transport(),
                    String.valueOf(h.targetRps()), String.valueOf(h.cancelRate()), tag, camp,
                    tpr, pv, ab);
            // Only contributing runs (e2e_p99 non-null) feed the headline test, matching
            // the cells.csv n_runs / e2e_p99_mean columns.
            double[] samples = e.getValue().stream()
                    .filter(r -> r.e2eP99() != null)
                    .mapToDouble(RunResult::e2eP99).toArray();
            CellRuns cell = new CellRuns(h, samples);
            Map<String, List<CellRuns>> bucket = "async".equals(h.stack())
                    ? asyncByShared : reactiveByShared;
            bucket.computeIfAbsent(sharedKey, k -> new ArrayList<>()).add(cell);
        }

        System.out.println();
        System.out.println("Headline paired tests (async vs reactive, per matched workload/transport/target_rps/cancel_rate/calibration_tag):");
        System.out.println("Verdict is gated on the non-parametric Mann-Whitney U test only — Welch's t-test is");
        System.out.println("printed for reference but its normality assumption is unreliable for per-run p99s,");
        System.out.println("which are extreme values; relying on (welch_p < alpha OR mw_p < alpha) would propagate");
        System.out.println("Welch false positives.");
        System.out.printf("%-72s %-7s %-10s %-7s %-10s %-12s %-12s %s%n",
                "shared_cell  [async pool/policy]", "a_n", "a_mean", "r_n", "r_mean",
                "welch_p", "mw_p", "verdict@alpha=" + alpha + " (mw_p)");

        asyncByShared.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    List<CellRuns> reactiveCells = reactiveByShared.get(entry.getKey());
                    if (reactiveCells == null || reactiveCells.isEmpty()) return;
                    CellRuns reactiveCell = reactiveCells.get(0);  // one reactive per shared key
                    double[] r = reactiveCell.samples();
                    if (r.length < 2) return;
                    for (CellRuns asyncCell : entry.getValue()) {
                        double[] a = asyncCell.samples();
                        if (a.length < 2) continue;
                        double welchP = tTest.tTest(a, r);
                        double mwP = mw.mannWhitneyUTest(a, r);
                        boolean significant = mwP < alpha;
                        String label = entry.getKey()
                                + "  [pool=" + asyncCell.head().poolSize()
                                + ",policy=" + asyncCell.head().rejectPolicy() + "]";
                        System.out.printf("%-72s %-7d %-10.2f %-7d %-10.2f %-12.4g %-12.4g %s%n",
                                label, a.length, mean(a), r.length, mean(r),
                                welchP, mwP, significant ? "DIFFERENT" : "ns");
                    }
                });
    }

    private record CellRuns(RunResult head, double[] samples) {
    }

    private double[] bootstrapMeanCi(double[] xs, int samples, double alpha) {
        if (xs.length == 0) return new double[]{Double.NaN, Double.NaN};
        if (xs.length == 1) return new double[]{xs[0], xs[0]};
        RandomDataGenerator rng = new RandomDataGenerator();
        double[] means = new double[samples];
        for (int i = 0; i < samples; i++) {
            double sum = 0;
            for (int j = 0; j < xs.length; j++) sum += xs[rng.nextInt(0, xs.length - 1)];
            means[i] = sum / xs.length;
        }
        DescriptiveStatistics ds = new DescriptiveStatistics(means);
        return new double[]{ds.getPercentile(alpha * 50), ds.getPercentile(100 - alpha * 50)};
    }

    private double mean(double[] xs) {
        if (xs.length == 0) return Double.NaN;
        double sum = 0;
        for (double x : xs) sum += x;
        return sum / xs.length;
    }

    private List<RunResult> loadRuns(Path csv) throws java.io.IOException {
        List<RunResult> out = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(csv)) {
            String header = r.readLine();
            if (header == null) return out;
            // Four CSV schemas in the wild:
            //   - Pre-2026-04-26 (19 cols): no failed-latency, no calibration_tag, no campaign
            //   - 2026-04-26 (23 cols):     adds e2e_failed_*, no calibration_tag
            //   - 2026-04-27 (24 cols):     adds calibration_tag at index 7
            //   - 2026-04-28+ (26 cols):    adds campaign + cell_status at indices 8,9
            boolean hasCampaign = header.contains("campaign");
            boolean hasCalibration = hasCampaign || header.contains("calibration_tag");
            // W0-residency metadata columns live at the tail of runs.csv,
            // after source_file. Resolve them by header so the positional parsing above
            // is untouched and older CSVs (without these columns) still load.
            java.util.List<String> cols = java.util.Arrays.asList(header.split(",", -1));
            int mtIdx = cols.indexOf("mock_tokens");
            int miIdx = cols.indexOf("mock_itl_ms");
            int wvIdx = cols.indexOf("workload_variant");
            // AI-streaming experimental axes + derived input-size metadata
            // (output-token sweep, prompt-length probe, mock-vs-live). Resolved
            // by header so older runs.csv files without these columns still load.
            int tprIdx = cols.indexOf("tokens_per_response");
            int pvIdx = cols.indexOf("prompt_variant");
            int abIdx = cols.indexOf("ai_backend");
            int pcIdx = cols.indexOf("prompt_chars");
            int patIdx = cols.indexOf("prompt_approx_tokens");
            // Classified-error columns (2026-06-14+ contamination split). Resolved by header.
            int[] errIdx = new int[RunResult.ERROR_TYPES.size()];
            for (int i = 0; i < errIdx.length; i++) errIdx[i] = cols.indexOf("err_" + RunResult.ERROR_TYPES.get(i));
            int rejIdx = cols.indexOf("reject_delta");
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split(",", -1);
                boolean hasFailed = hasCalibration || f.length >= 23;
                int idx = 0;
                String stack = f[idx++];
                String workload = f[idx++];
                String transport = f[idx++];
                int rps = Integer.parseInt(emptyTo(f[idx++], "0"));
                String poolSize = f[idx++];
                String rejectPolicy = f[idx++];
                double cancelRate = Double.parseDouble(emptyTo(f[idx++], "0"));
                String calibrationTag = hasCalibration ? f[idx++] : "";
                String campaign = hasCampaign ? f[idx++] : "early-pilot-2026-04-27";
                String cellStatus = hasCampaign ? f[idx++] : "NO_VALIDATION_SIDECAR";
                long requests = Long.parseLong(emptyTo(f[idx++], "0"));
                long errors = Long.parseLong(emptyTo(f[idx++], "0"));
                long cancels = Long.parseLong(emptyTo(f[idx++], "0"));
                long dropped = Long.parseLong(emptyTo(f[idx++], "0"));
                Double ttftP50 = parseD(f[idx++]);
                Double ttftP95 = parseD(f[idx++]);
                Double ttftP99 = parseD(f[idx++]);
                Double e2eP50 = parseD(f[idx++]);
                Double e2eP95 = parseD(f[idx++]);
                Double e2eP99 = parseD(f[idx++]);
                Double e2eP999 = parseD(f[idx++]);
                Double e2eFailedP50 = hasFailed ? parseD(f[idx++]) : null;
                Double e2eFailedP95 = hasFailed ? parseD(f[idx++]) : null;
                Double e2eFailedP99 = hasFailed ? parseD(f[idx++]) : null;
                long e2eFailedCount = hasFailed ? Long.parseLong(emptyTo(f[idx++], "0")) : 0L;
                String sourceFile = idx < f.length ? f[idx] : "";
                Integer mockTokens = (mtIdx >= 0 && mtIdx < f.length) ? parseIntOrNull(f[mtIdx]) : null;
                Integer mockItlMs = (miIdx >= 0 && miIdx < f.length) ? parseIntOrNull(f[miIdx]) : null;
                String workloadVariant = (wvIdx >= 0 && wvIdx < f.length) ? f[wvIdx] : "";
                java.util.Map<String, Integer> errorsByType = new java.util.LinkedHashMap<>();
                for (int i = 0; i < errIdx.length; i++) {
                    Integer v = (errIdx[i] >= 0 && errIdx[i] < f.length) ? parseIntOrNull(f[errIdx[i]]) : null;
                    errorsByType.put(RunResult.ERROR_TYPES.get(i), v == null ? 0 : v);
                }
                Integer rejectDelta = (rejIdx >= 0 && rejIdx < f.length) ? parseIntOrNull(f[rejIdx]) : null;
                Integer tokensPerResponse = (tprIdx >= 0 && tprIdx < f.length) ? parseIntOrNull(f[tprIdx]) : null;
                String promptVariant = (pvIdx >= 0 && pvIdx < f.length) ? f[pvIdx] : "";
                String aiBackend = (abIdx >= 0 && abIdx < f.length) ? f[abIdx] : "";
                Integer promptChars = (pcIdx >= 0 && pcIdx < f.length) ? parseIntOrNull(f[pcIdx]) : null;
                Integer promptApproxTokens = (patIdx >= 0 && patIdx < f.length) ? parseIntOrNull(f[patIdx]) : null;
                out.add(new RunResult(stack, workload, transport, rps, poolSize,
                        rejectPolicy, cancelRate, calibrationTag, campaign, cellStatus,
                        requests, errors, cancels, dropped,
                        ttftP50, ttftP95, ttftP99,
                        e2eP50, e2eP95, e2eP99, e2eP999,
                        e2eFailedP50, e2eFailedP95, e2eFailedP99, e2eFailedCount,
                        sourceFile, mockTokens, mockItlMs, workloadVariant,
                        errorsByType, rejectDelta,
                        tokensPerResponse, promptVariant, aiBackend,
                        promptChars, promptApproxTokens));
            }
        }
        return out;
    }

    private Double parseD(String s) {
        return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
    }

    private Integer parseIntOrNull(String s) {
        return (s == null || s.isEmpty()) ? null : Integer.valueOf(s.trim());
    }

    private String emptyTo(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private String fmt(double d) {
        return Double.isNaN(d) ? "" : String.format("%.3f", d);
    }

    private String intOrEmpty(Integer i) {
        return i == null ? "" : String.valueOf(i);
    }

    /** Per-cell CPU/heap aggregates pooled across the cell's runs' {@code *_resources.csv}. */
    private record ResourceAgg(double cpuAvg, double cpuP95, double cpuMax,
                               double heapAvgMb, double heapMaxMb,
                               double heapCommittedAvgMb, double heapCommittedMaxMb,
                               int samples) {
    }

    /**
     * Reads each run's sibling {@code <c>_<i>_resources.csv} (derived from the .txt
     * source path written by run_bench.sh) and pools CPU% + heap-used + heap-committed
     * samples across the whole cell. CPU p95 is the cell-level percentile over all
     * pooled samples; heap reports avg + max. Missing/NaN samples and absent files are
     * skipped so a cell without resource sampling still aggregates to empty columns.
     */
    private ResourceAgg aggregateResources(List<RunResult> runs) {
        DescriptiveStatistics cpu = new DescriptiveStatistics();
        DescriptiveStatistics heap = new DescriptiveStatistics();
        DescriptiveStatistics committed = new DescriptiveStatistics();
        for (RunResult r : runs) {
            String src = r.sourceFile();
            if (src == null || !src.endsWith(".txt")) continue;
            Path res = Path.of(src.substring(0, src.length() - 4) + "_resources.csv");
            if (!Files.exists(res)) continue;
            try (BufferedReader br = Files.newBufferedReader(res)) {
                String header = br.readLine();
                if (header == null) continue;
                List<String> hc = java.util.Arrays.asList(header.split(",", -1));
                int ci = hc.indexOf("cpu_usage_pct");
                int hi = hc.indexOf("heap_used_mb");
                int ki = hc.indexOf("heap_committed_mb");
                String ln;
                while ((ln = br.readLine()) != null) {
                    String[] f = ln.split(",", -1);
                    addIfNum(cpu, ci, f);
                    addIfNum(heap, hi, f);
                    addIfNum(committed, ki, f);
                }
            } catch (java.io.IOException ignored) {
                // A run without a readable resources CSV simply contributes no samples.
            }
        }
        return new ResourceAgg(
                cpu.getN() > 0 ? cpu.getMean() : Double.NaN,
                cpu.getN() > 0 ? cpu.getPercentile(95) : Double.NaN,
                cpu.getN() > 0 ? cpu.getMax() : Double.NaN,
                heap.getN() > 0 ? heap.getMean() : Double.NaN,
                heap.getN() > 0 ? heap.getMax() : Double.NaN,
                committed.getN() > 0 ? committed.getMean() : Double.NaN,
                committed.getN() > 0 ? committed.getMax() : Double.NaN,
                (int) cpu.getN());
    }

    private void addIfNum(DescriptiveStatistics ds, int idx, String[] f) {
        if (idx < 0 || idx >= f.length) return;
        String v = f[idx];
        if (v == null || v.isEmpty() || "NaN".equals(v)) return;
        try {
            ds.addValue(Double.parseDouble(v));
        } catch (NumberFormatException ignored) {
            // tolerate a malformed sample row
        }
    }
}
