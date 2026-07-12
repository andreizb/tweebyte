import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { GatewayRegistry } from './gateway-registry.service';
import { AppConfig } from './app-config.model';

const TWO_GW: AppConfig = {
  activeGatewayId: 'a',
  gateways: [
    { id: 'a', label: 'Primary', baseUrl: 'http://a.test/' },
    { id: 'b', label: 'Secondary', baseUrl: 'http://b.test' }
  ],
  useMock: false,
  healthPollMs: 0,
  showLatency: true
};

describe('GatewayRegistry', () => {
  let registry: GatewayRegistry;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [GatewayRegistry] });
    registry = TestBed.inject(GatewayRegistry);
  });

  it('applies config and trims a trailing slash off the active base URL', () => {
    registry.applyConfig(TWO_GW);
    expect(registry.activeId()).toBe('a');
    expect(registry.activeBaseUrl()).toBe('http://a.test'); // no trailing slash
    expect(registry.activeDescriptor().label).toBe('Primary');
    expect(registry.hasMultiple()).toBe(true);
  });

  it('setActive switches the active gateway and persists it', () => {
    registry.applyConfig(TWO_GW);
    registry.setActive('b');
    expect(registry.activeId()).toBe('b');
    expect(registry.activeBaseUrl()).toBe('http://b.test');
    expect(sessionStorage.getItem('tb.activeGateway')).toBe('b');
  });

  it('strips multiple trailing slashes from active and arbitrary base URLs', () => {
    registry.applyConfig({
      ...TWO_GW,
      gateways: [
        { id: 'a', label: 'Primary', baseUrl: 'http://a.test///' },
        { id: 'b', label: 'Secondary', baseUrl: 'http://b.test////' }
      ]
    });
    expect(registry.activeBaseUrl()).toBe('http://a.test');
    expect(registry.baseUrlFor('b')).toBe('http://b.test');
  });

  it('ignores setActive for unknown or unchanged ids', () => {
    registry.applyConfig(TWO_GW);
    registry.setActive('a'); // unchanged
    registry.setActive('ghost'); // unknown
    expect(registry.activeId()).toBe('a');
  });

  it('rehydrates a persisted active gateway on applyConfig', () => {
    sessionStorage.setItem('tb.activeGateway', 'b');
    registry.applyConfig(TWO_GW);
    expect(registry.activeId()).toBe('b');
  });

  it('ignores a persisted id that is not in the configured set', () => {
    sessionStorage.setItem('tb.activeGateway', 'stale');
    registry.applyConfig(TWO_GW);
    expect(registry.activeId()).toBe('a');
  });

  it('descriptorFor / baseUrlFor resolve by id (and tolerate unknowns)', () => {
    registry.applyConfig(TWO_GW);
    expect(registry.descriptorFor('b')?.label).toBe('Secondary');
    expect(registry.baseUrlFor('b')).toBe('http://b.test');
    expect(registry.descriptorFor('nope')).toBeUndefined();
    expect(registry.baseUrlFor('nope')).toBe('');
  });

  it('hasMultiple is false for a single-gateway config', () => {
    registry.applyConfig({ ...TWO_GW, gateways: [TWO_GW.gateways[0]], activeGatewayId: 'a' });
    expect(registry.hasMultiple()).toBe(false);
  });

  it('keeps a same-origin gateway as an empty active base URL', () => {
    registry.applyConfig({
      ...TWO_GW,
      activeGatewayId: 'same',
      gateways: [{ id: 'same', label: 'Same origin', baseUrl: '' }]
    });
    expect(registry.activeBaseUrl()).toBe('');
    expect(registry.baseUrlFor('same')).toBe('');
  });
});
