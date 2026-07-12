import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { tbMock } from './scenario';

/**
 * MSW browser worker. Started by the APP_INITIALIZER when config.useMock is true,
 * BEFORE the first HttpClient request, so the SPA runs fully without Spring.
 */
export const worker = setupWorker(...handlers);

export async function startMockWorker(): Promise<void> {
  await worker.start({
    // Don't warn for endpoints we haven't mocked yet — let them 404/pass through.
    onUnhandledRequest: 'bypass',
    quiet: false,
    // Root-absolute URL + scope: a relative SW URL resolves against the document URL, so
    // on a deep-link reload (e.g. /tweet/:id) it would 404, reject worker.start(), and
    // blank the app. Anchoring at root makes the mock boot from any route depth.
    serviceWorker: { url: '/mockServiceWorker.js', options: { scope: '/' } }
  });
  // Expose the scenario hook so e2e specs can drive error/empty/latency states that the
  // Service Worker would otherwise mask from Playwright's network layer. Test-only; this
  // whole module is tree-shaken out of non-mock production builds.
  (globalThis as unknown as { __tbMock?: typeof tbMock }).__tbMock = tbMock;
  // eslint-disable-next-line no-console
  console.info('[tweebyte] MSW mock backend active — no Spring required.');
}
