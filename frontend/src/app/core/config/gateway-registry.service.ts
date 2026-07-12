import { computed, Injectable, signal } from '@angular/core';
import {
  AppConfig,
  DEFAULT_APP_CONFIG,
  GatewayDescriptor,
  GatewayId
} from './app-config.model';

const ACTIVE_GATEWAY_STORAGE_KEY = 'tb.activeGateway';

/**
 * The single backend seam — backend-agnostic.
 *
 * Holds the loaded runtime config and one mutable `active` gateway id. The
 * `baseUrlInterceptor` injects this service and reads {@link activeBaseUrl} on EVERY
 * request, so selecting a different gateway routes the NEXT request to that URL with no
 * reload. The bearer is attached by a separate interceptor from the session store, so it
 * survives a gateway change unchanged (the gateways are interchangeable by contract).
 *
 * Nothing here — and nothing downstream — branches on which gateway is active. A gateway
 * is just an `{ id, label, baseUrl }`; the id is opaque.
 */
@Injectable({ providedIn: 'root' })
export class GatewayRegistry {
  private readonly _config = signal<AppConfig>(DEFAULT_APP_CONFIG);
  private readonly _activeId = signal<GatewayId>(DEFAULT_APP_CONFIG.activeGatewayId);

  /** The whole loaded config (read-only view). */
  readonly config = this._config.asReadonly();
  /** Currently active gateway id. */
  readonly activeId = this._activeId.asReadonly();

  /** All configured gateways, in selector order. */
  readonly gateways = computed<GatewayDescriptor[]>(() => this._config().gateways);

  /** Descriptor for the active gateway (falls back to the first configured one). */
  readonly activeDescriptor = computed<GatewayDescriptor>(() => {
    const list = this._config().gateways;
    return list.find((g) => g.id === this._activeId()) ?? list[0];
  });

  /** Base URL the interceptor prefixes onto relative requests. No trailing slash. */
  readonly activeBaseUrl = computed<string>(() =>
    normalizeBaseUrl(this.activeDescriptor()?.baseUrl ?? '')
  );

  /** Whether more than one interchangeable gateway is configured (selector is useful). */
  readonly hasMultiple = computed<boolean>(() => this._config().gateways.length > 1);

  /** Whether MSW should mock the network. */
  readonly useMock = computed<boolean>(() => this._config().useMock);

  /** Called once by the APP_INITIALIZER after fetching assets/config.json. */
  applyConfig(config: AppConfig): void {
    this._config.set(config);
    const persisted = this.readPersistedActive(config);
    this._activeId.set(persisted ?? config.activeGatewayId);
  }

  /** Select a gateway by id. Affects the next request immediately; no reload. */
  setActive(id: GatewayId): void {
    if (id === this._activeId() || !this._config().gateways.some((g) => g.id === id)) {
      return;
    }
    this._activeId.set(id);
    this.persistActive(id);
  }

  /** Resolve a gateway descriptor by id. */
  descriptorFor(id: GatewayId): GatewayDescriptor | undefined {
    return this._config().gateways.find((g) => g.id === id);
  }

  /** Absolute base URL for an arbitrary gateway id. */
  baseUrlFor(id: GatewayId): string {
    return normalizeBaseUrl(this.descriptorFor(id)?.baseUrl ?? '');
  }

  private persistActive(id: GatewayId): void {
    try {
      sessionStorage.setItem(ACTIVE_GATEWAY_STORAGE_KEY, id);
    } catch {
      /* storage unavailable (private mode) — non-fatal */
    }
  }

  private readPersistedActive(config: AppConfig): GatewayId | null {
    try {
      const v = sessionStorage.getItem(ACTIVE_GATEWAY_STORAGE_KEY);
      return v && config.gateways.some((g) => g.id === v) ? v : null;
    } catch {
      return null;
    }
  }
}

function normalizeBaseUrl(url: string): string {
  return url.replace(/\/+$/, '');
}
