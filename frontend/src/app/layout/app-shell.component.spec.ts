import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AppShellComponent } from './app-shell.component';
import { GatewayRegistry } from '../core/config/gateway-registry.service';
import { SessionStore } from '../core/state/session.store';
import { HealthService } from '../core/api/services/health.service';
import { TweetService } from '../core/api/services/tweet.service';
import { RecommendationService } from '../core/api/services/recommendation.service';
import { FollowService } from '../core/api/services/follow.service';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

async function renderShell() {
  return render(AppShellComponent, {
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      // Embedded rails pull in these services; keep them inert (no network, no timers).
      { provide: HealthService, useValue: { check: vi.fn().mockReturnValue(of('up')) } },
      { provide: TweetService, useValue: { popularHashtags: vi.fn().mockReturnValue(of([])) } },
      { provide: RecommendationService, useValue: { whoToFollow: vi.fn().mockReturnValue(of([])) } },
      {
        provide: FollowService,
        useValue: {
          // The right rail reads the viewer's followed-id set to seed who-to-follow rows.
          followersIdentifiers: vi.fn().mockReturnValue(of([])),
          follow: vi.fn().mockReturnValue(of(undefined)),
          unfollow: vi.fn().mockReturnValue(of(undefined))
        }
      }
    ],
    configureTestBed: (tb) => {
      tb.inject(GatewayRegistry).applyConfig({
        activeGatewayId: 'gw',
        gateways: [{ id: 'gw', label: 'Gateway', baseUrl: 'http://gw' }],
        useMock: false,
        healthPollMs: 0,
        showLatency: false
      });
      tb.inject(SessionStore).setToken(makeToken('me'));
    }
  });
}

describe('AppShellComponent', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders the 3-column shell with a main region and a router outlet', async () => {
    await renderShell();
    const main = screen.getByRole('main');
    expect(main).toBeTruthy();
    expect(main.querySelector('router-outlet')).toBeTruthy();
  });

  it('exposes the session owner id to the rails', async () => {
    const { fixture } = await renderShell();
    expect(fixture.componentInstance.userId()).toBe('me');
  });

  it('stays backend-agnostic: the shell chrome names no implementation', async () => {
    await renderShell();
    // NEUTRALITY: nothing in the rendered shell hints at how the app is served.
    expect(document.body.textContent).not.toMatch(/async|reactive|webflux|mvc|blocking|event-loop/i);
  });
});
