import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/angular';
import { of } from 'rxjs';
import { GatewaySelectorComponent } from './gateway-selector.component';
import { GatewayRegistry } from '../core/config/gateway-registry.service';
import { LatencyService } from '../core/state/latency.service';
import { ToastService } from '../core/state/toast.service';
import { HealthService } from '../core/api/services/health.service';
import { AppConfig } from '../core/config/app-config.model';

const SINGLE: AppConfig = {
  activeGatewayId: 'only',
  gateways: [{ id: 'only', label: 'Gateway', baseUrl: 'http://only.test' }],
  useMock: false,
  healthPollMs: 0,
  showLatency: true
};

const MULTI: AppConfig = {
  activeGatewayId: 'a',
  gateways: [
    { id: 'a', label: 'Primary', baseUrl: 'http://a' },
    { id: 'b', label: 'Secondary', baseUrl: 'http://b' }
  ],
  useMock: false,
  healthPollMs: 0,
  showLatency: true
};

async function renderSelector(config: AppConfig) {
  // The embedded HealthDot transitively needs HealthService.
  const result = await render(GatewaySelectorComponent, {
    providers: [
      { provide: HealthService, useValue: { check: vi.fn().mockReturnValue(of('up')) } }
    ],
    configureTestBed: (tb) => {
      tb.inject(GatewayRegistry).applyConfig(config);
    }
  });
  await result.fixture.whenStable();
  result.fixture.detectChanges();
  return result;
}

describe('GatewaySelectorComponent', () => {
  beforeEach(() => sessionStorage.clear());

  describe('single gateway', () => {
    it('shows the single gateway label and its base URL, with no radio buttons', async () => {
      await renderSelector(SINGLE);
      // hasMultiple() is false => no choice surface, just the active label + URL.
      expect(document.querySelectorAll('[role="radio"]').length).toBe(0);
      expect(screen.getByText('Gateway')).toBeTruthy();
      // The base URL is rendered in a font-mono <p>.
      const urlEl = document.querySelector('p.font-mono');
      expect(urlEl?.textContent).toContain('http://only.test');
    });
  });

  describe('multiple gateways', () => {
    it('renders two labelled radio buttons', async () => {
      await renderSelector(MULTI);
      const radios = document.querySelectorAll('[role="radio"]');
      expect(radios.length).toBe(2);
      expect(screen.getByText('Primary')).toBeTruthy();
      expect(screen.getByText('Secondary')).toBeTruthy();
    });

    it('clicking Secondary makes it active and pushes a neutral info toast', async () => {
      const { fixture } = await renderSelector(MULTI);
      const registry = fixture.debugElement.injector.get(GatewayRegistry);
      const toast = fixture.debugElement.injector.get(ToastService);

      expect(registry.activeId()).toBe('a');
      await fireEvent.click(screen.getByText('Secondary'));
      fixture.detectChanges();

      // The selection routes the NEXT request to the other URL — no reload.
      expect(registry.activeId()).toBe('b');
      expect(toast.toasts().some((t) => t.kind === 'info')).toBe(true);

      // Neutrality: nothing in the toast (or label) leaks a backend paradigm.
      expect(JSON.stringify(toast.toasts())).not.toMatch(/async|reactive|webflux|mvc/i);
    });
  });

  describe('latency badge', () => {
    it('shows "42 ms" once a round-trip is recorded', async () => {
      const { fixture } = await renderSelector(SINGLE);
      const latency = fixture.debugElement.injector.get(LatencyService);
      latency.record(42);
      fixture.detectChanges();
      expect(fixture.componentInstance.latencyLabel()).toBe('42 ms');
    });

    it('shows "—" when no latency has been observed', async () => {
      const { fixture } = await renderSelector(SINGLE);
      expect(fixture.componentInstance.latencyLabel()).toBe('—');
    });
  });
});
