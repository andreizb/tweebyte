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
        String sourceFile
) {
    /**
     * Cell key includes calibration_tag and campaign so operationally
     * distinct batches stay separate even when their load dimensions match.
     */
    public String cellKey() {
        return String.join("|", stack, workload, transport,
                String.valueOf(targetRps), poolSize, rejectPolicy,
                String.valueOf(cancelRate),
                calibrationTag == null ? "" : calibrationTag,
                campaign == null ? "" : campaign);
    }
}
