import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { HealthApiService } from './health.service';
import { GatewayRegistry } from '../../config/gateway-registry.service';

describe('HealthApiService', () => {
  let service: HealthApiService;
  let registry: GatewayRegistry;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [HealthApiService, GatewayRegistry] });
    service = TestBed.inject(HealthApiService);
    registry = TestBed.inject(GatewayRegistry);
    registry.applyConfig({
      activeGatewayId: 'a',
      gateways: [
        { id: 'a', label: 'A', baseUrl: 'http://a.test' },
        { id: 'b', label: 'B', baseUrl: 'http://b.test' }
      ],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    });
  });

  afterEach(() => { vi.restoreAllMocks(); });

  it('returns "up" when the active gateway reports UP', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP' }), { status: 200 })
    );
    expect(await firstValueFrom(service.check())).toBe('up');
    expect(spy).toHaveBeenCalledWith('http://a.test/actuator/health', expect.any(Object));
  });

  it('checks a specific gateway id when provided', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP' }), { status: 200 })
    );
    await firstValueFrom(service.check('b'));
    expect(spy).toHaveBeenCalledWith('http://b.test/actuator/health', expect.any(Object));
  });

  it('returns "down" on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 503 }));
    expect(await firstValueFrom(service.check())).toBe('down');
  });

  it('returns "down" when fetch rejects', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network'));
    expect(await firstValueFrom(service.check())).toBe('down');
  });

  it('defaults missing status to UP', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 200 }));
    expect(await firstValueFrom(service.check())).toBe('up');
  });

  it('returns "unknown" when the gateway id is unresolvable', async () => {
    expect(await firstValueFrom(service.check('does-not-exist'))).toBe('unknown');
  });

  it('probes a relative /actuator/health when the active gateway has an empty baseUrl (same-origin)', async () => {
    registry.applyConfig({
      activeGatewayId: 'same-origin',
      gateways: [{ id: 'same-origin', label: 'Same origin', baseUrl: '' }],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    });
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP' }), { status: 200 })
    );
    expect(await firstValueFrom(service.check())).toBe('up');
    expect(spy).toHaveBeenCalledWith('/actuator/health', expect.any(Object));
  });

  it('probes a relative /actuator/health for an explicit empty-baseUrl gateway id', async () => {
    registry.applyConfig({
      activeGatewayId: 'a',
      gateways: [
        { id: 'a', label: 'A', baseUrl: 'http://a.test' },
        { id: 'origin', label: 'Origin', baseUrl: '' }
      ],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    });
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP' }), { status: 200 })
    );
    expect(await firstValueFrom(service.check('origin'))).toBe('up');
    expect(spy).toHaveBeenCalledWith('/actuator/health', expect.any(Object));
  });
});
