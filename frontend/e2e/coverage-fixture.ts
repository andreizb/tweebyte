import { test as base } from '@playwright/test';
import { CoverageReport } from 'monocart-coverage-reports';
import { coverageOptions } from './coverage.config';

/**
 * Playwright test fixture that collects V8 JS coverage per test (only when E2E_COVERAGE=1)
 * and adds it to the shared monocart cache under `coverageOptions.outputDir`. The merged
 * HTML + summary is produced by the global teardown (e2e/coverage-teardown.ts).
 *
 * When E2E_COVERAGE is unset this is a transparent pass-through of the stock `test`, so the
 * normal suite pays no coverage cost.
 */
const COLLECT = process.env['E2E_COVERAGE'] === '1';

export const test = base.extend<{ autoCoverage: void }>({
  autoCoverage: [
    async ({ page, browserName }, use): Promise<void> => {
      // V8 coverage is a Chromium-only CDP capability.
      const enabled = COLLECT && browserName === 'chromium';
      if (enabled) {
        await page.coverage.startJSCoverage({ resetOnNavigation: false });
      }

      await use();

      if (!enabled) {
        return;
      }
      const jsCoverage = await page.coverage.stopJSCoverage();
      // All instances sharing this outputDir append to the same on-disk cache; the suite
      // runs single-worker (playwright.config.ts), so there is no write contention. The
      // global teardown merges the cache into the final report.
      const report = new CoverageReport(coverageOptions);
      await report.add(jsCoverage);
    },
    { auto: true }
  ]
});

export { expect } from '@playwright/test';
