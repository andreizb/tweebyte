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
            defaultValue = "OK,LEGACY-NO-VALIDATION",
            description = "Comma-separated list of cell_status values to include in the report. Runs whose "
                    + "cell_status is not in this list are dropped from aggregates entirely (they still appear "
                    + "in runs.csv). Default keeps OK runs and pre-2026-04-28 runs that predate the validation "
                    + "sidecar. Set to e.g. 'OK' for a strict manuscript export, or 'OK,LEGACY-NO-VALIDATION,"
                    + "CONTAMINATED' to include everything.")
    String includeStatus;

    @CommandLine.Option(names = "--filter-campaign",
            description = "Optional comma-separated list of campaign labels. When set, only runs whose "
                    + "campaign appears in the list contribute to the report. Use to produce a manuscript-only "
                    + "cells.csv from a single campaign without changing the runs.csv schema.")
    String filterCampaign;

    @Override
    public Integer call() throws Exception {
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
                    + "total_requests,total_errors,total_dropped,error_rate,"
                    + "quarantined_n_runs,quarantined_requests,quarantined_errors,quarantined_failed_p99_mean");
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

                long qReq = quarantined.stream().mapToLong(RunResult::requests).sum();
                long qErr = quarantined.stream().mapToLong(RunResult::errors).sum();
                double[] qFailedP99s = quarantined.stream()
                        .filter(r -> r.e2eFailedP99() != null).mapToDouble(RunResult::e2eFailedP99).toArray();

                double[] e2eCi = bootstrapMeanCi(e2eP99s, bootstrapSamples, alpha);
                double[] ttftCi = bootstrapMeanCi(ttftP99s, bootstrapSamples, alpha);
                double[] e2eFailedCi = bootstrapMeanCi(e2eFailedP99s, bootstrapSamples, alpha);

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
                        String.valueOf(totalDropped), fmt(errorRate),
                        String.valueOf(quarantined.size()),
                        String.valueOf(qReq), String.valueOf(qErr),
                        fmt(mean(qFailedP99s))));
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
        // Campaign added 2026-04-28 so headline-5rep cells don't pair against
        // diagonal cells that happen to share the same load shape.
        TTest tTest = new TTest();
        MannWhitneyUTest mw = new MannWhitneyUTest();

        Map<String, List<CellRuns>> asyncByShared = new LinkedHashMap<>();
        Map<String, List<CellRuns>> reactiveByShared = new LinkedHashMap<>();
        for (var e : byCell.entrySet()) {
            RunResult h = e.getValue().get(0);
            String tag = h.calibrationTag() == null ? "" : h.calibrationTag();
            String camp = h.campaign() == null ? "" : h.campaign();
            String sharedKey = String.join("|", h.workload(), h.transport(),
                    String.valueOf(h.targetRps()), String.valueOf(h.cancelRate()), tag, camp);
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
                String campaign = hasCampaign ? f[idx++] : "pre-cleanup-pilot-2026-04-27";
                String cellStatus = hasCampaign ? f[idx++] : "LEGACY-NO-VALIDATION";
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
                out.add(new RunResult(stack, workload, transport, rps, poolSize,
                        rejectPolicy, cancelRate, calibrationTag, campaign, cellStatus,
                        requests, errors, cancels, dropped,
                        ttftP50, ttftP95, ttftP99,
                        e2eP50, e2eP95, e2eP99, e2eP999,
                        e2eFailedP50, e2eFailedP95, e2eFailedP99, e2eFailedCount,
                        sourceFile));
            }
        }
        return out;
    }

    private Double parseD(String s) {
        return (s == null || s.isEmpty()) ? null : Double.parseDouble(s);
    }

    private String emptyTo(String s, String dflt) {
        return (s == null || s.isEmpty()) ? dflt : s;
    }

    private String fmt(double d) {
        return Double.isNaN(d) ? "" : String.format("%.3f", d);
    }
}
