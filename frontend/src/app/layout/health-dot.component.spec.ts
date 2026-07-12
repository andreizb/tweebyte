import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render } from '@testing-library/angular';
import { of } from 'rxjs';
import { HealthDotComponent } from './health-dot.component';
import { HealthService } from '../core/api/services/health.service';
import { GatewayRegistry } from '../core/config/gateway-registry.service';

function seedRegistry(tb: { inject: (t: typeof GatewayRegistry) => GatewayRegistry }): void {
  tb.inject(GatewayRegistry).applyConfig({
    activeGatewayId: 'g',
    gateways: [{ id: 'g', label: 'Gateway', baseUrl: 'http://gw.test' }],
    useMock: false,
    // 0 => single check, no timer (avoids fake-timer complexity).
    healthPollMs: 0,
    showLatency: true
  });
}

async function renderDot(opts: {
  state?: 'up' | 'down' | 'unknown';
  gatewayId?: string;
} = {}) {
  const check = vi.fn().mockReturnValue(of(opts.state ?? 'up'));
  const result = await render(HealthDotComponent, {
    providers: [{ provide: HealthService, useValue: { check } }],
    componentInputs: opts.gatewayId ? { gatewayId: opts.gatewayId } : {},
    configureTestBed: (tb) => seedRegistry(tb)
  });
  await result.fixture.whenStable();
  result.fixture.detectChanges();
  return { ...result, check };
}

function dot(): HTMLElement {
  const el = document.querySelector('span[role="img"]');
  if (!el) {
    throw new Error('health dot span[role="img"] not found');
  }
  return el as HTMLElement;
}

describe('HealthDotComponent', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders a status dot (span with role="img")', async () => {
    await renderDot();
    expect(dot()).toBeTruthy();
  });

  it('after a single "up" check the dot is coloured bg-retweet (healthy)', async () => {
    await renderDot({ state: 'up' });
    expect(dot().classList.contains('bg-retweet')).toBe(true);
    expect(dot().classList.contains('bg-destructive')).toBe(false);
  });

  it('after a single "down" check the dot is coloured bg-destructive (unreachable)', async () => {
    await renderDot({ state: 'down' });
    expect(dot().classList.contains('bg-destructive')).toBe(true);
    expect(dot().classList.contains('bg-retweet')).toBe(false);
  });

  it('title() carries the gateway label and a healthy phrase when up', async () => {
    const { fixture } = await renderDot({ state: 'up' });
    const title = fixture.componentInstance.title();
    expect(title).toContain('Gateway');
    expect(title).toContain('healthy');
  });

  it('title() reports unreachable when down', async () => {
    const { fixture } = await renderDot({ state: 'down' });
    expect(fixture.componentInstance.title()).toContain('unreachable');
  });

  it('checks the active gateway when no id is supplied (neutral default)', async () => {
    const { check } = await renderDot();
    // Called with undefined => the active gateway, with no notion of what serves it.
    expect(check).toHaveBeenCalledWith(undefined);
  });

  it('passing a gatewayId input checks that specific gateway', async () => {
    const { check } = await renderDot({ gatewayId: 'that-id' });
    expect(check).toHaveBeenCalledWith('that-id');
  });
});
