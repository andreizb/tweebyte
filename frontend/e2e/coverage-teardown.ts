import { readFile } from 'node:fs/promises';
import { CoverageReport } from 'monocart-coverage-reports';
import { coverageOptions } from './coverage.config';

const COVERAGE_THRESHOLD = 90;

/**
 * Playwright globalTeardown — merges the per-test V8 coverage cache into the final report
 * (HTML + summary in ./coverage-e2e), prints the headline percentages, and enforces the
 * line + branch coverage floor. No-op unless E2E_COVERAGE=1.
 */
export default async function globalTeardown(): Promise<void> {
  if (process.env['E2E_COVERAGE'] !== '1') {
    return;
  }
  const report = new CoverageReport(coverageOptions);
  const results = await report.generate();
  const generatedSummary = (results?.summary ?? {}) as Record<string, { pct?: number } | undefined>;
  const jsonSummary = await readJsonSummary();
  const lines = metricPct('lines', jsonSummary, generatedSummary);
  const branches = metricPct('branches', jsonSummary, generatedSummary);
  // eslint-disable-next-line no-console
  console.log(
    `\n[e2e coverage] lines ${lines ?? 'n/a'}%, branches ${branches ?? 'n/a'}% ` +
      `(threshold ${COVERAGE_THRESHOLD}%) — full report: coverage-e2e/index.html\n`
  );

  const failures = [
    metricFailure('lines', lines),
    metricFailure('branches', branches)
  ].filter((failure): failure is string => Boolean(failure));
  if (failures.length > 0) {
    throw new Error(`E2E coverage threshold failed: ${failures.join(', ')}`);
  }
}

function metricFailure(metric: string, pct: number | undefined): string | undefined {
  if (typeof pct !== 'number') {
    return `${metric} missing`;
  }
  return pct >= COVERAGE_THRESHOLD ? undefined : `${metric} ${pct}% < ${COVERAGE_THRESHOLD}%`;
}

async function readJsonSummary(): Promise<Record<string, { pct?: number } | undefined> | undefined> {
  try {
    const raw = await readFile(`${coverageOptions.outputDir}/coverage-summary.json`, 'utf8');
    const parsed = JSON.parse(raw) as {
      total?: Record<string, { pct?: number } | undefined>;
    };
    return parsed.total;
  } catch {
    return undefined;
  }
}

function metricPct(
  metric: string,
  jsonSummary: Record<string, { pct?: number } | undefined> | undefined,
  generatedSummary: Record<string, { pct?: number } | undefined>
): number | undefined {
  return jsonSummary?.[metric]?.pct ?? generatedSummary[metric]?.pct;
}
