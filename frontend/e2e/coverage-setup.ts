import { CoverageReport } from 'monocart-coverage-reports';
import { coverageOptions } from './coverage.config';

/**
 * Playwright globalSetup — clears any stale monocart coverage cache before the run so the
 * advisory report reflects only this run. No-op unless E2E_COVERAGE=1.
 */
export default async function globalSetup(): Promise<void> {
  if (process.env['E2E_COVERAGE'] !== '1') {
    return;
  }
  const report = new CoverageReport(coverageOptions);
  report.cleanCache();
}
