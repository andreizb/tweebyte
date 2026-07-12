import { describe, expect, it } from 'vitest';
import { normalizeConfig } from './config-loader';
import { DEFAULT_APP_CONFIG } from './app-config.model';

describe('normalizeConfig', () => {
  it('returns the defaults for null/empty input', () => {
    expect(normalizeConfig(null)).toEqual(DEFAULT_APP_CONFIG);
    expect(normalizeConfig({})).toEqual(DEFAULT_APP_CONFIG);
  });

  it('returns the defaults for primitive parsed values', () => {
    expect(normalizeConfig('bad config')).toEqual(DEFAULT_APP_CONFIG);
    expect(normalizeConfig(42)).toEqual(DEFAULT_APP_CONFIG);
  });

  it('keeps a valid full config', () => {
    const cfg = {
      activeGatewayId: 'b',
      gateways: [
        { id: 'a', label: 'A', baseUrl: 'http://a' },
        { id: 'b', label: 'B', baseUrl: 'http://b' }
      ],
      useMock: false,
      healthPollMs: 5000,
      showLatency: false
    };
    expect(normalizeConfig(cfg)).toEqual(cfg);
  });

  it('drops gateways with no baseUrl and synthesizes id/label when missing', () => {
    const out = normalizeConfig({
      gateways: [{ baseUrl: 'http://only-url' }, { id: 'x', label: 'X' }]
    });
    expect(out.gateways).toEqual([{ id: 'gateway-1', label: 'Gateway 1', baseUrl: 'http://only-url' }]);
  });

  it('ignores non-object gateways and gateways with non-string base URLs', () => {
    const out = normalizeConfig({
      gateways: [null, 'x', { id: 'bad', label: 'Bad', baseUrl: 123 }, { id: 'ok', label: 'OK', baseUrl: 'http://ok' }]
    } as unknown);
    expect(out.gateways).toEqual([{ id: 'ok', label: 'OK', baseUrl: 'http://ok' }]);
  });

  it('synthesizes only blank gateway ids or labels', () => {
    const out = normalizeConfig({
      gateways: [
        { id: '', label: 'Relative', baseUrl: '/gateway' },
        { id: 'kept', label: '', baseUrl: 'http://kept' }
      ]
    });
    expect(out.gateways).toEqual([
      { id: 'gateway-1', label: 'Relative', baseUrl: '/gateway' },
      { id: 'kept', label: 'Gateway 2', baseUrl: 'http://kept' }
    ]);
  });

  it('keeps an empty baseUrl as a valid same-origin gateway (relative requests)', () => {
    const out = normalizeConfig({
      gateways: [{ id: 'gateway', label: 'Gateway', baseUrl: '' }]
    });
    expect(out.gateways).toEqual([{ id: 'gateway', label: 'Gateway', baseUrl: '' }]);
  });

  it('keeps root-relative base URLs for reverse-proxied deployments', () => {
    const out = normalizeConfig({
      gateways: [{ id: 'gateway', label: 'Gateway', baseUrl: '/api' }]
    });
    expect(out.gateways[0].baseUrl).toBe('/api');
  });

  it('falls back to the default gateway when none are valid', () => {
    const out = normalizeConfig({ gateways: [{ label: 'no url' }] });
    expect(out.gateways).toEqual(DEFAULT_APP_CONFIG.gateways);
  });

  it('falls back to the first gateway when activeGatewayId is unknown', () => {
    const out = normalizeConfig({
      activeGatewayId: 'ghost',
      gateways: [{ id: 'real', label: 'Real', baseUrl: 'http://real' }]
    });
    expect(out.activeGatewayId).toBe('real');
  });

  it('coerces invalid scalar fields back to defaults', () => {
    const out = normalizeConfig({
      useMock: 'yes',
      healthPollMs: -10,
      showLatency: 'true'
    } as unknown);
    expect(out.useMock).toBe(DEFAULT_APP_CONFIG.useMock);
    expect(out.healthPollMs).toBe(DEFAULT_APP_CONFIG.healthPollMs);
    expect(out.showLatency).toBe(DEFAULT_APP_CONFIG.showLatency);
  });

  it('accepts healthPollMs of 0 (polling disabled)', () => {
    expect(normalizeConfig({ healthPollMs: 0 }).healthPollMs).toBe(0);
  });
});
