/// <reference types="vitest" />
import angular from '@analogjs/vite-plugin-angular';
import { defineConfig } from 'vitest/config';

/**
 * Vitest config for the Tweebyte frontend.
 *
 * Uses the AnalogJS Angular plugin so component/standalone specs compile through the
 * Angular AOT pipeline, runs in jsdom, and reports v8 coverage. The coverage gate is
 * scoped to the application code we author (app + mocks); generated/bootstrap shims are
 * excluded so the ≥90% bar reflects real units.
 */
export default defineConfig({
  plugins: [angular()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test-setup.ts'],
    include: ['src/**/*.spec.ts'],
    reporters: ['default'],
    pool: 'threads',
    coverage: {
      provider: 'v8',
      all: true,
      reporter: ['text', 'text-summary', 'html', 'json-summary'],
      reportsDirectory: './coverage',
      include: [
        'src/app/core/**/*.ts',
        'src/app/shared/**/*.ts',
        'src/app/layout/**/*.ts',
        'src/app/features/**/*.ts'
      ],
      exclude: [
        '**/*.spec.ts',
        'src/app/**/index.ts',
        'src/test-setup.ts',
        // Pure type/interface declarations carry no executable statements.
        'src/app/core/api/models/**',
        'src/app/core/config/app-config.model.ts'
      ],
      thresholds: {
        statements: 90,
        branches: 90,
        functions: 90,
        lines: 90
      }
    }
  },
  define: {
    'import.meta.vitest': 'undefined'
  }
});
