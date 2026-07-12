import { CoverageReportOptions } from 'monocart-coverage-reports';

/**
 * Advisory e2e coverage (monocart-coverage-reports).
 *
 * Collects V8 JS coverage via Playwright's `page.coverage` and maps it back to the Angular
 * TypeScript sources through the dev-server sourcemaps — NO build instrumentation. Active
 * only when `E2E_COVERAGE=1` (see e2e/coverage-fixture.ts + playwright.config.ts), so the
 * normal `npx playwright test` run is unaffected.
 *
 * The report is generated only by `npm run e2e:coverage`. Its global teardown enforces
 * the same headline floor as unit coverage for lines + branches, while ordinary Playwright
 * runs stay threshold-free.
 */
export const coverageOptions: CoverageReportOptions = {
  name: 'Tweebyte e2e coverage (advisory)',
  outputDir: './coverage-e2e',
  reports: [['v8'], ['html'], ['console-summary'], ['json-summary']],

  // entryFilter runs over the RAW V8 entries (the served bundle URLs). The Angular dev server
  // pre-bundles its DEPENDENCIES (Angular + RxJS) under .angular/cache/.../vite/deps/. Those
  // carry NO sourcemap back into src/app/, so they would otherwise appear as their OWN entries
  // in the report (framework code we don't drive) and silently dilute the percentages — the
  // sourceFilter below never sees a src/app/ path for them, so it can't drop them. Reject the
  // dep pre-bundles HERE by path. The app's own served entries (esbuild `chunk-*.js` /
  // `main.js`, which DO sourcemap into src/app/) MUST pass through — their bytes are attributed
  // to the real src/app/*.ts sources, which the sourceFilter then narrows.
  entryFilter: (entry: { url?: string }) =>
    !((entry.url ?? '').includes('/vite/deps/') || (entry.url ?? '').includes('/.angular/cache/')),
  sourceFilter: (sourcePath: string) =>
    sourcePath.includes('src/app/') &&
    !sourcePath.includes('/src/mocks/') &&
    !sourcePath.endsWith('.spec.ts'),

  // Collapse the per-test raw coverage into one merged report.
  all: {
    dir: ['./src/app'],
    filter: {
      '**/*.spec.ts': false,
      '**/*.ts': true
    }
  }
};
