/**
 * Vitest global setup. Initializes the Angular TestBed for the jsdom environment via the
 * AnalogJS helper, then installs the handful of browser APIs jsdom lacks but our
 * components rely on (object URLs, matchMedia, IntersectionObserver).
 */
import '@analogjs/vite-plugin-angular/setup-vitest';

import { getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting
} from '@angular/platform-browser-dynamic/testing';

getTestBed().initTestEnvironment(BrowserDynamicTestingModule, platformBrowserDynamicTesting(), {
  teardown: { destroyAfterEach: true }
});

// --- jsdom shims -----------------------------------------------------------------

// Object URLs (Avatar / MediaThumb blob images).
if (!('createObjectURL' in URL)) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (URL as any).createObjectURL = (): string => 'blob:mock';
}
if (!('revokeObjectURL' in URL)) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (URL as any).revokeObjectURL = (): void => undefined;
}

// matchMedia (prefers-reduced-motion, responsive checks).
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false
    }) as MediaQueryList;
}

// IntersectionObserver (infinite-scroll sentinel).
if (!('IntersectionObserver' in globalThis)) {
  class MockIntersectionObserver implements IntersectionObserver {
    readonly root = null;
    readonly rootMargin = '';
    readonly thresholds: readonly number[] = [];
    observe(): void {
      /* no-op */
    }
    unobserve(): void {
      /* no-op */
    }
    disconnect(): void {
      /* no-op */
    }
    takeRecords(): IntersectionObserverEntry[] {
      return [];
    }
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).IntersectionObserver = MockIntersectionObserver;
}
