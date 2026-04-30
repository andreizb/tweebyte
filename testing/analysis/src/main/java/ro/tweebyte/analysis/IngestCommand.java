package ro.tweebyte.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(name = "ingest",
        description = "Walk a results directory, extract the embedded JSON summary from each k6 run, and emit a flat CSV.")
public class IngestCommand implements Callable<Integer> {

    @CommandLine.Option(names = "--results-root", required = true,
            description = "Root directory containing k6 result batch subdirectories.")
    Path resultsRoot;

    @CommandLine.Option(names = "--out", required = true,
            description = "Output CSV path.")
    Path outPath;

    @CommandLine.Option(names = "--untagged-calibration-cutoff",
            defaultValue = "20260427_133900",
            description = "Result-dir timestamp threshold (yyyyMMdd_HHmmss). Result dirs with a "
                    + "timestamp before this cutoff are tagged with --untagged-pre-cutoff-tag if "
                    + "their JSON summary doesn't carry an explicit calibration_tag; dirs at or "
                    + "after the cutoff get --untagged-post-cutoff-tag. Default: 2026-04-27 13:39:00 EEST, "
                    + "the moment the calibrated W1 batch started against the zero-inflation mock.")
    String untaggedCalibrationCutoff;

    @CommandLine.Option(names = "--untagged-pre-cutoff-tag",
            defaultValue = "mock-defaults",
            description = "Calibration tag applied to untagged result dirs whose timestamp is before the cutoff.")
    String untaggedPreCutoffTag;

    @CommandLine.Option(names = "--untagged-post-cutoff-tag",
            defaultValue = "qwen-3.5-4b-mlx-v1",
            description = "Calibration tag applied to untagged result dirs whose timestamp is at or after the cutoff "
                    + "and whose JSON summary doesn't carry an explicit calibration_tag.")
    String untaggedPostCutoffTag;

    @CommandLine.Option(names = "--campaign-manifest",
            description = "Optional path to a 2-column TSV mapping result-batch dir name → campaign label. "
                    + "Manifest entries override the campaign label from the run's k6 handleSummary JSON; "
                    + "use this to relabel batches whose execution-time --ai-campaign should not be used, "
                    + "or to assign a campaign to result dirs that lack one in their JSON. "
                    + "Lines starting with '#' are comments. If a dir is not listed, the run's JSON "
                    + "campaign field wins; if that is also missing, the dir's timestamp is matched against "
                    + "--campaign-cutoffs (yyyyMMdd_HHmmss=label comma-separated, ascending) to assign "
                    + "a fallback. If no rule matches, the campaign defaults to 'early-pilot-2026-04-27'.")
    Path campaignManifest;

    @CommandLine.Option(names = "--campaign-cutoffs",
            defaultValue = "20260427_193900=headline-5rep-2026-04-27,20260427_224500=diagonal-2026-04-28",
            description = "Comma-separated list of yyyyMMdd_HHmmss=label pairs, ascending. A result dir whose "
                    + "timestamp is at or after a cutoff (and before the next one) gets the cutoff's label. "
                    + "Anything before the earliest cutoff defaults to 'early-pilot-2026-04-27'.")
    String campaignCutoffsRaw;

    private static final DateTimeFormatter DIR_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern DIR_TS_PATTERN = Pattern.compile("(\\d{8})_(\\d{6})");

    private java.util.Map<String, String> campaignManifestMap = java.util.Collections.emptyMap();
    private java.util.List<java.util.Map.Entry<LocalDateTime, String>> campaignCutoffs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        LocalDateTime cutoff = LocalDateTime.parse(untaggedCalibrationCutoff, DIR_TS);
        loadCampaignManifest();
        loadCampaignCutoffs();
        List<RunResult> results = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(resultsRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .filter(p -> !p.getFileName().toString().endsWith("_resources.csv"))
                    .filter(p -> !p.getFileName().toString().endsWith("_validation.txt"))
                    .filter(p -> !p.getFileName().toString().endsWith("_prom.csv"))
                    .forEach(p -> parse(p, cutoff).ifPresent(results::add));
        }
        System.out.printf("Parsed %d k6 runs under %s%n", results.size(), resultsRoot);
        java.util.Map<String, Long> tagCounts = new java.util.TreeMap<>();
        for (RunResult r : results) tagCounts.merge(r.calibrationTag(), 1L, Long::sum);
        for (var e : tagCounts.entrySet()) {
            System.out.printf("  calibration_tag=%-30s n=%d%n", e.getKey(), e.getValue());
        }
        java.util.Map<String, Long> campCounts = new java.util.TreeMap<>();
        for (RunResult r : results) campCounts.merge(r.campaign(), 1L, Long::sum);
        for (var e : campCounts.entrySet()) {
            System.out.printf("  campaign=%-40s n=%d%n", e.getKey(), e.getValue());
        }
        java.util.Map<String, Long> statusCounts = new java.util.TreeMap<>();
        for (RunResult r : results) statusCounts.merge(r.cellStatus(), 1L, Long::sum);
        for (var e : statusCounts.entrySet()) {
            System.out.printf("  cell_status=%-25s n=%d%n", e.getKey(), e.getValue());
        }
        writeCsv(outPath, results);
        System.out.printf("Wrote %s%n", outPath.toAbsolutePath());
        return 0;
    }

    private void loadCampaignManifest() throws IOException {
        if (campaignManifest == null || !Files.exists(campaignManifest)) return;
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (String line : Files.readAllLines(campaignManifest)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 2) map.put(parts[0], parts[1]);
        }
        this.campaignManifestMap = map;
    }

    private void loadCampaignCutoffs() {
        if (campaignCutoffsRaw == null || campaignCutoffsRaw.isBlank()) return;
        for (String token : campaignCutoffsRaw.split(",")) {
            String[] kv = token.split("=", 2);
            if (kv.length != 2) continue;
            try {
                campaignCutoffs.add(java.util.Map.entry(
                        LocalDateTime.parse(kv[0].trim(), DIR_TS),
                        kv[1].trim()));
            } catch (Exception ignored) {}
        }
        campaignCutoffs.sort(java.util.Map.Entry.comparingByKey());
    }

    private java.util.Optional<RunResult> parse(Path file, LocalDateTime cutoff) {
        try {
            String content = Files.readString(file);
            int startIdx = content.indexOf('{');
            if (startIdx < 0) return java.util.Optional.empty();
            String json = content.substring(startIdx).trim();
            JsonNode root = new ObjectMapper().readTree(json);
            if (!root.has("workload")) return java.util.Optional.empty();
            String calibrationTag = resolveCalibrationTag(root, file, cutoff);
            String campaign = resolveCampaign(root, file);
            String cellStatus = resolveCellStatus(file);
            return java.util.Optional.of(new RunResult(
                    root.path("stack").asText(""),
                    root.path("workload").asText(""),
                    root.path("transport").asText(""),
                    root.path("target_rps").asInt(0),
                    root.path("pool_size").asText(""),
                    root.path("reject_policy").asText(""),
                    root.path("cancel_rate").asDouble(0.0),
                    calibrationTag,
                    campaign,
                    cellStatus,
                    root.path("requests").asLong(0),
                    root.path("errors").asLong(0),
                    root.path("cancels").asLong(0),
                    root.path("dropped").asLong(0),
                    nullOrDouble(root, "ttft_ms", "p50"),
                    nullOrDouble(root, "ttft_ms", "p95"),
                    nullOrDouble(root, "ttft_ms", "p99"),
                    nullOrDouble(root, "e2e_ms", "p50"),
                    nullOrDouble(root, "e2e_ms", "p95"),
                    nullOrDouble(root, "e2e_ms", "p99"),
                    nullOrDouble(root, "e2e_ms", "p999"),
                    nullOrDouble(root, "e2e_failed_ms", "p50"),
                    nullOrDouble(root, "e2e_failed_ms", "p95"),
                    nullOrDouble(root, "e2e_failed_ms", "p99"),
                    root.path("e2e_failed_ms").path("count").asLong(0),
                    file.toString()));
        } catch (Exception e) {
            System.err.printf("skip %s: %s%n", file, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Three sources, in priority order:
     *   1. Explicit {@code calibration_tag} in the k6 handleSummary JSON
     *      (set by run_bench.sh + ai-streaming-benchmark.js).
     *   2. Result-dir timestamp + cutoff (untagged-run backfill).
     *   3. {@code --untagged-pre-cutoff-tag} (default: "mock-defaults") if the
     *      timestamp couldn't be parsed.
     */
    private String resolveCalibrationTag(JsonNode root, Path file, LocalDateTime cutoff) {
        JsonNode tag = root.path("calibration_tag");
        if (tag.isTextual() && !tag.asText().isBlank()) return tag.asText();
        // Walk up to find the result-batch dir name, e.g. results_ai_streaming_20260427_092546_gwrQqH
        for (Path p = file.getParent(); p != null; p = p.getParent()) {
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            Matcher m = DIR_TS_PATTERN.matcher(name);
            if (m.find()) {
                try {
                    LocalDateTime ts = LocalDateTime.parse(m.group(1) + "_" + m.group(2), DIR_TS);
                    return ts.isBefore(cutoff) ? untaggedPreCutoffTag : untaggedPostCutoffTag;
                } catch (Exception ignored) {
                    break;
                }
            }
        }
        return untaggedPreCutoffTag;
    }

    /**
     * Four sources, in priority order:
     *   1. Manifest TSV (--campaign-manifest) keyed by result-batch dir name.
     *      AUTHORITATIVE — overrides the JSON's baked-in campaign so an
     *      operator can relabel a batch whose execution-time
     *      --ai-campaign flag (e.g., one of two early-attempt batches that
     *      bear the canonical label but operationally belong to a different
     *      campaign for cell-key partitioning purposes).
     *   2. Explicit {@code campaign} in the k6 handleSummary JSON.
     *   3. Result-dir timestamp matched against --campaign-cutoffs.
     *   4. Fallback "early-pilot-2026-04-27".
     */
    private String resolveCampaign(JsonNode root, Path file) {
        // 1. Manifest wins outright.
        for (Path p = file.getParent(); p != null; p = p.getParent()) {
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            if (campaignManifestMap.containsKey(name)) return campaignManifestMap.get(name);
        }
        // 2. Run's JSON.
        JsonNode campNode = root.path("campaign");
        if (campNode.isTextual() && !campNode.asText().isBlank()
                && !"ad-hoc".equals(campNode.asText())) {
            return campNode.asText();
        }
        // 3. Timestamp cutoffs.
        for (Path p = file.getParent(); p != null; p = p.getParent()) {
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            Matcher m = DIR_TS_PATTERN.matcher(name);
            if (m.find()) {
                try {
                    LocalDateTime ts = LocalDateTime.parse(m.group(1) + "_" + m.group(2), DIR_TS);
                    String label = "early-pilot-2026-04-27";
                    for (var entry : campaignCutoffs) {
                        if (!ts.isBefore(entry.getKey())) label = entry.getValue();
                        else break;
                    }
                    return label;
                } catch (Exception ignored) {
                    break;
                }
            }
        }
        // 4. Fallback.
        return "early-pilot-2026-04-27";
    }

    /**
     * Reads the {@code <c>_<i>_validation.txt} sidecar emitted by run_bench.sh.
     * When present, returns either {@code OK}, {@code CONTAMINATED} (transport-error
     * signatures inside the run window), or {@code READINESS_FAIL} (cell skipped
     * because the SUT didn't pass the pre-warmup gate). Runs without the sidecar
     * default to {@code NO_VALIDATION_SIDECAR} so downstream consumers can choose
     * whether to trust them.
     */
    private String resolveCellStatus(Path file) {
        try {
            String fname = file.getFileName().toString();
            if (!fname.endsWith(".txt")) return "NO_VALIDATION_SIDECAR";
            String stem = fname.substring(0, fname.length() - 4);
            Path sidecar = file.resolveSibling(stem + "_validation.txt");
            if (!Files.exists(sidecar)) return "NO_VALIDATION_SIDECAR";
            for (String line : Files.readAllLines(sidecar)) {
                if (line.startsWith("cell_status=")) return line.substring("cell_status=".length()).trim();
            }
        } catch (Exception ignored) {}
        return "NO_VALIDATION_SIDECAR";
    }

    private Double nullOrDouble(JsonNode root, String group, String key) {
        JsonNode n = root.path(group).path(key);
        return n.isNumber() ? n.asDouble() : null;
    }

    private void writeCsv(Path out, List<RunResult> results) throws IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(out)) {
            w.write("stack,workload,transport,target_rps,pool_size,reject_policy,cancel_rate,calibration_tag,"
                    + "campaign,cell_status,"
                    + "requests,errors,cancels,dropped,"
                    + "ttft_p50,ttft_p95,ttft_p99,e2e_p50,e2e_p95,e2e_p99,e2e_p999,"
                    + "e2e_failed_p50,e2e_failed_p95,e2e_failed_p99,e2e_failed_count,source_file");
            w.newLine();
            for (RunResult r : results) {
                w.write(String.join(",",
                        csv(r.stack()), csv(r.workload()), csv(r.transport()),
                        String.valueOf(r.targetRps()), csv(r.poolSize()),
                        csv(r.rejectPolicy()), String.valueOf(r.cancelRate()),
                        csv(r.calibrationTag()),
                        csv(r.campaign()),
                        csv(r.cellStatus()),
                        String.valueOf(r.requests()), String.valueOf(r.errors()),
                        String.valueOf(r.cancels()), String.valueOf(r.dropped()),
                        fmt(r.ttftP50()), fmt(r.ttftP95()), fmt(r.ttftP99()),
                        fmt(r.e2eP50()), fmt(r.e2eP95()), fmt(r.e2eP99()), fmt(r.e2eP999()),
                        fmt(r.e2eFailedP50()), fmt(r.e2eFailedP95()), fmt(r.e2eFailedP99()),
                        String.valueOf(r.e2eFailedCount()),
                        csv(r.sourceFile())));
                w.newLine();
            }
        }
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private String fmt(Double d) {
        return d == null ? "" : String.format("%.3f", d);
    }
}
