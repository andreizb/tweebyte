import { inject } from '@angular/core';
import {
  AppConfig,
  DEFAULT_APP_CONFIG,
  DEFAULT_GATEWAY,
  GatewayDescriptor
} from './app-config.model';
import { GatewayRegistry } from './gateway-registry.service';

/**
 * APP_INITIALIZER factory: fetch assets/config.json, hydrate the GatewayRegistry, and
 * (if useMock) start the MSW mock backend BEFORE the app makes its first request.
 *
 * Always resolves — a missing/invalid config falls back to DEFAULT_APP_CONFIG so the
 * app boots regardless. Uses bare `fetch` (no HttpClient) to avoid interceptor ordering
 * concerns during bootstrap.
 */
export function loadAppConfig(): () => Promise<void> {
  const registry = inject(GatewayRegistry);

  return async () => {
    const config = await fetchConfig();
    registry.applyConfig(config);

    if (config.useMock) {
      // Lazy import so MSW is tree-shaken out of non-mock production builds.
      const { startMockWorker } = await import('../../../mocks/browser');
      await startMockWorker();
    }
  };
}

async function fetchConfig(): Promise<AppConfig> {
  try {
    // Root-absolute so it resolves the same from any route depth. `fetch` resolves
    // relative URLs against the document URL (NOT <base href>), so a relative path would
    // 404 on a deep-link reload (e.g. /profile/:id) and silently drop the gateway config.
    const res = await fetch('/assets/config.json', { cache: 'no-store' });
    if (!res.ok) {
      return DEFAULT_APP_CONFIG;
    }
    const parsed = (await res.json()) as unknown;
    return normalizeConfig(parsed);
  } catch {
    return DEFAULT_APP_CONFIG;
  }
}

/**
 * Coerce an arbitrary parsed JSON value into a valid AppConfig, layering it over the
 * defaults so partial/malformed files still boot. Pure (exported for unit tests).
 */
export function normalizeConfig(parsed: unknown): AppConfig {
  const partial = (parsed ?? {}) as Partial<AppConfig>;

  const gateways = normalizeGateways(partial.gateways);
  const activeGatewayId =
    typeof partial.activeGatewayId === 'string' &&
    gateways.some((g) => g.id === partial.activeGatewayId)
      ? partial.activeGatewayId
      : gateways[0].id;

  return {
    activeGatewayId,
    gateways,
    useMock:
      typeof partial.useMock === 'boolean' ? partial.useMock : DEFAULT_APP_CONFIG.useMock,
    healthPollMs:
      typeof partial.healthPollMs === 'number' && partial.healthPollMs >= 0
        ? partial.healthPollMs
        : DEFAULT_APP_CONFIG.healthPollMs,
    showLatency:
      typeof partial.showLatency === 'boolean'
        ? partial.showLatency
        : DEFAULT_APP_CONFIG.showLatency
  };
}

function normalizeGateways(input: unknown): GatewayDescriptor[] {
  if (!Array.isArray(input)) {
    return [...DEFAULT_APP_CONFIG.gateways];
  }
  const valid = input
    .filter((g): g is Partial<GatewayDescriptor> => !!g && typeof g === 'object')
    // An empty baseUrl ("") is VALID and means SAME-ORIGIN (relative requests) — the
    // containerised deployment where nginx serves the SPA and reverse-proxies the API to
    // the gateway, so the browser never needs an absolute gateway URL or CORS. Only a
    // missing or non-string baseUrl is rejected.
    .filter((g) => typeof g.baseUrl === 'string')
    .map((g, i) => ({
      id: typeof g.id === 'string' && g.id.length > 0 ? g.id : `gateway-${i + 1}`,
      label: typeof g.label === 'string' && g.label.length > 0 ? g.label : `Gateway ${i + 1}`,
      baseUrl: g.baseUrl as string
    }));
  return valid.length > 0 ? valid : [DEFAULT_GATEWAY];
}
