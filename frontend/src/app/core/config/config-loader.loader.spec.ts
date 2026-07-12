import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { runInInjectionContext } from '@angular/core';
import { loadAppConfig } from './config-loader';
import { GatewayRegistry } from './gateway-registry.service';
import { DEFAULT_APP_CONFIG } from './app-config.model';

// MSW worker is lazily imported by loadAppConfig when useMock is true; stub it so the
// initializer doesn't try to spin a real service worker in jsdom.
vi.mock('../../../mocks/browser', () => ({ startMockWorker: vi.fn().mockResolvedValue(undefined) }));

describe('loadAppConfig (APP_INITIALIZER)', () => {
  let registry: GatewayRegistry;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [GatewayRegistry] });
    registry = TestBed.inject(GatewayRegistry);
  });

  afterEach(() => { vi.restoreAllMocks(); });

  function run(): Promise<void> {
    return runInInjectionContext(TestBed, () => loadAppConfig()());
  }

  it('fetches assets/config.json and applies it to the registry', async () => {
    const cfg = {
      activeGatewayId: 'b',
      gateways: [
        { id: 'a', label: 'A', baseUrl: 'http://a' },
        { id: 'b', label: 'B', baseUrl: 'http://b' }
      ],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    };
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(cfg), { status: 200 }));
    await run();
    expect(registry.activeId()).toBe('b');
    expect(registry.activeBaseUrl()).toBe('http://b');
    // Root-absolute so deep-link reloads (e.g. /profile/:id) still find the config.
    expect(fetchSpy).toHaveBeenCalledWith('/assets/config.json', expect.anything());
  });

  it('falls back to defaults when the config response is not ok', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 404 }));
    await run();
    expect(registry.gateways()).toEqual(DEFAULT_APP_CONFIG.gateways);
  });

  it('falls back to defaults when the fetch rejects', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('offline'));
    await run();
    expect(registry.activeId()).toBe(DEFAULT_APP_CONFIG.activeGatewayId);
  });

  it('starts the MSW worker when useMock is true', async () => {
    const { startMockWorker } = await import('../../../mocks/browser');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ useMock: true, gateways: [{ id: 'g', label: 'G', baseUrl: 'http://g' }], activeGatewayId: 'g' }), {
        status: 200
      })
    );
    await run();
    expect(startMockWorker).toHaveBeenCalled();
  });

  it('does NOT start the MSW worker when useMock is false', async () => {
    const { startMockWorker } = await import('../../../mocks/browser');
    (startMockWorker as ReturnType<typeof vi.fn>).mockClear();
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ useMock: false, gateways: [{ id: 'g', label: 'G', baseUrl: 'http://g' }], activeGatewayId: 'g' }), {
        status: 200
      })
    );
    await run();
    expect(startMockWorker).not.toHaveBeenCalled();
  });
});
