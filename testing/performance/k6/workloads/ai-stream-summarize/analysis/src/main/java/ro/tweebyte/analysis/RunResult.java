package ro.tweebyte.analysis;

/**
 * One k6 iteration's flattened summary. Identifiers together describe
 * the cell; numeric fields are the per-run statistics.
 *
 * <p>{@code calibrationTag} distinguishes batches of runs that share
 * the same load-shape dimensions (stack/workload/transport/rps/pool/policy/cancel)
 * but were produced under different mock parameters (e.g. mock defaults vs.
 * the calibrated Qwen3.5-4B-MLX zero-inflated mock). It is part of {@link #cellKey()}
 * so the report/plot pipelines do not silently pool calibrated and uncalibrated
 * results into the same row.
 *
 * <p>The trailing {@code tokensPerResponse}, {@code promptVariant}, and
 * {@code aiBackend} fields ARE part of {@link #cellKey()} because they are
 * independent experimental axes — the configured output-token sweep, the
 * prompt-length probe, and the mock-vs-live backend switch — that must never
 * pool together. Each is appended to the key with an empty-string default, so
 * every pre-existing run (which lacks these fields) keeps grouping identically:
 * the change is backward-safe. {@code promptChars} and {@code promptApproxTokens}
 * are derived input-size metadata, NOT axes, so they are recorded per row but
 * deliberately excluded from the cell key.
 */
public record RunResult(
        String stack,
        String workload,
        String transport,
        int targetRps,
        String poolSize,
        String rejectPolicy,
        double cancelRate,
        String calibrationTag,
        String campaign,
        String cellStatus,
        long requests,
        long errors,
        long cancels,
        long dropped,
        Double ttftP50,
        Double ttftP95,
        Double ttftP99,
        Double e2eP50,
        Double e2eP95,
        Double e2eP99,
        Double e2eP999,
        Double e2eFailedP50,
        Double e2eFailedP95,
        Double e2eFailedP99,
        long e2eFailedCount,
        String sourceFile,
        // Descriptive W0-residency metadata (appended so the constructor and
        // cellKey() stay positionally stable). Not part of the cell key — the campaign
        // label already partitions the workload variants; these columns
        // make the timing auditable per row. Null/blank for pre-instrumentation runs.
        Integer mockTokens,
        Integer mockItlMs,
        String workloadVariant,
        // Classified failed-request population (from the k6 summary's errors_by_type)
        // plus reject_delta (authoritative AbortPolicy count from the per-cell
        // *_prom.csv rejections_total last-first). Lets the report split transport/
        // admission collapse from genuine application-level pool rejection. Keyed by
        // {@link #ERROR_TYPES}; absent/old runs leave it empty and rejectDelta null.
        java.util.Map<String, Integer> errorsByType,
        Integer rejectDelta,
        // Three independent experimental axes (in cellKey()) + two derived
        // input-size metadata columns (NOT in cellKey()). Appended at the tail so
        // the canonical constructor and the leading cellKey() segments stay
        // positionally stable. Empty/null for runs predating these fields.
        Integer tokensPerResponse,
        String promptVariant,
        String aiBackend,
        Integer promptChars,
        Integer promptApproxTokens
) {
    /** Fixed column order for the classified error breakdown in runs.csv. */
    public static final java.util.List<String> ERROR_TYPES = java.util.List.of(
            "http_4xx", "http_5xx", "dial_timeout", "req_timeout", "addr_unavail",
            "conn_reset", "conn_refused", "eof", "lgen", "other");

    /** Status==0 transport/admission failures (everything except application 4xx/5xx). */
    public int transportErrors() {
        if (errorsByType == null) return 0;
        int n = 0;
        for (String k : ERROR_TYPES) {
            if (!k.startsWith("http_")) n += errorsByType.getOrDefault(k, 0);
        }
        return n;
    }

    public int errorsOfType(String type) {
        return errorsByType == null ? 0 : errorsByType.getOrDefault(type, 0);
    }
    /**
     * Cell key includes calibration_tag and campaign so operationally
     * distinct batches stay separate even when their load dimensions match.
     */
    public String cellKey() {
        return String.join("|", stack, workload, transport,
                String.valueOf(targetRps), poolSize, rejectPolicy,
                String.valueOf(cancelRate),
                calibrationTag == null ? "" : calibrationTag,
                campaign == null ? "" : campaign,
                tokensPerResponse == null ? "" : String.valueOf(tokensPerResponse),
                promptVariant == null ? "" : promptVariant,
                aiBackend == null ? "" : aiBackend);
    }
}
