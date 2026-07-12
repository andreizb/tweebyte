/**
 * Runtime configuration shape (loaded from assets/config.json at startup).
 *
 * BACKEND-AGNOSTIC BY DESIGN. The frontend talks to ONE gateway base URL and neither
 * knows nor cares what implementation is behind it — every gateway exposes byte-identical
 * `/{service}/**` routes. A "swap", if the operator configures more than one gateway, is
 * purely changing the active base-URL string; the SAME code path serves every gateway.
 *
 * One built bundle targets any gateway (or set of interchangeable gateways) without a
 * rebuild: an operator edits config.json next to index.html.
 */

/** Opaque, operator-chosen identifier for a configured gateway. Never branched on. */
export type GatewayId = string;

export interface GatewayDescriptor {
  /** Stable id used to select the active gateway and persist the choice. */
  id: GatewayId;
  /** Neutral display label for the environment/URL selector, e.g. "Gateway" or "Staging". */
  label: string;
  /** Gateway base URL, e.g. "http://localhost:8080". No trailing slash required. */
  baseUrl: string;
}

export interface AppConfig {
  /** id of the gateway active on first load. Must match one of `gateways`. */
  activeGatewayId: GatewayId;
  /** One or more interchangeable gateways. Order is the selector order. */
  gateways: GatewayDescriptor[];
  /** When true, MSW intercepts network calls (no real backend needed). */
  useMock: boolean;
  /** Health poll interval (ms) for the active-gateway health dot. 0 disables polling. */
  healthPollMs: number;
  /** When true, show the round-trip latency badge for the active gateway. */
  showLatency: boolean;
}

/** The single default gateway used when nothing is configured. */
export const DEFAULT_GATEWAY: GatewayDescriptor = {
  id: 'gateway',
  label: 'Gateway',
  baseUrl: 'http://localhost:8080'
};

/**
 * Built-in fallback used if assets/config.json is missing or fails to parse, so the app
 * always boots. A single gateway — no implementation is named or assumed.
 */
export const DEFAULT_APP_CONFIG: AppConfig = {
  activeGatewayId: DEFAULT_GATEWAY.id,
  gateways: [DEFAULT_GATEWAY],
  useMock: true,
  healthPollMs: 10_000,
  showLatency: true
};
