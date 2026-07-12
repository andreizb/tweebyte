import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RightRailComponent } from './right-rail.component';
import { TweetService } from '../core/api/services/tweet.service';
import { RecommendationService } from '../core/api/services/recommendation.service';
import { FollowService } from '../core/api/services/follow.service';
import { HealthService } from '../core/api/services/health.service';
import { SessionStore } from '../core/state/session.store';
import { ViewerStateService } from '../core/state/viewer-state.service';
import { GatewayRegistry } from '../core/config/gateway-registry.service';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

async function renderRail(
  opts: {
    hashtags?: ReturnType<typeof vi.fn>;
    whoToFollow?: ReturnType<typeof vi.fn>;
    followedIds?: ReturnType<typeof vi.fn>;
  } = {}
) {
  const popularHashtags =
    opts.hashtags ?? vi.fn().mockReturnValue(of([{ id: 'h1', text: 'angular', count: 5 }]));
  const whoToFollow =
    opts.whoToFollow ?? vi.fn().mockReturnValue(of([{ id: 'u2', user_name: 'bob' }]));
  // ViewerStateService is mocked at the component boundary; its real behaviour is covered
  // by viewer-state.service.spec. followedIds drives the who-to-follow rows' followed state.
  const followedIds =
    opts.followedIds ?? vi.fn().mockReturnValue(of(new Set<string>() as ReadonlySet<string>));

  const result = await render(RightRailComponent, {
    providers: [
      provideRouter([]),
      { provide: TweetService, useValue: { popularHashtags } },
      { provide: RecommendationService, useValue: { whoToFollow } },
      { provide: ViewerStateService, useValue: { followedIds } },
      // Embedded FollowButton needs FollowService; embedded HealthDot needs HealthService.
      {
        provide: FollowService,
        useValue: {
          follow: vi.fn().mockReturnValue(of(undefined)),
          unfollow: vi.fn().mockReturnValue(of(undefined))
        }
      },
      { provide: HealthService, useValue: { check: vi.fn().mockReturnValue(of('up')) } }
    ],
    configureTestBed: (tb) => {
      // Seed a single neutral gateway (no timer) so the embedded selector/dot are happy.
      tb.inject(GatewayRegistry).applyConfig({
        activeGatewayId: 'g',
        gateways: [{ id: 'g', label: 'Gateway', baseUrl: 'http://gw.test' }],
        useMock: false,
        healthPollMs: 0,
        showLatency: true
      });
      tb.inject(SessionStore).setToken(makeToken('me'));
    }
  });
  await result.fixture.whenStable();
  result.fixture.detectChanges();
  return { ...result, popularHashtags, whoToFollow, followedIds };
}

describe('RightRailComponent', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders Trends with #angular', async () => {
    await renderRail();
    expect(screen.getByText('Trends')).toBeTruthy();
    expect(screen.getByText('#angular')).toBeTruthy();
  });

  it('renders Who to follow with the suggested user bob', async () => {
    await renderRail();
    expect(screen.getByText('Who to follow')).toBeTruthy();
    // user_name shows as the display name (and again as @handle).
    expect(screen.getAllByText('bob').length).toBeGreaterThan(0);
  });

  it('seeds a suggested account the viewer already follows to its Following state', async () => {
    // The viewer's followed-id set contains the suggestion (u2), so its row renders
    // 'Following' rather than the default 'Follow'.
    const { followedIds } = await renderRail({
      followedIds: vi.fn().mockReturnValue(of(new Set(['u2']) as ReadonlySet<string>))
    });
    expect(followedIds).toHaveBeenCalledWith('me');
    expect(screen.getByText('Following')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Follow' })).toBeNull();
  });

  it('leaves a suggestion on Follow when it is not in the viewer\'s followed set', async () => {
    await renderRail({
      followedIds: vi.fn().mockReturnValue(of(new Set(['someone-else']) as ReadonlySet<string>))
    });
    expect(screen.getByRole('button', { name: 'Follow' })).toBeTruthy();
  });

  describe('formatCount()', () => {
    it('compacts millions, thousands, and leaves small counts as-is', async () => {
      const { fixture } = await renderRail();
      const c = fixture.componentInstance;
      expect(c.formatCount(1_500_000)).toBe('1.5M');
      expect(c.formatCount(2500)).toBe('2.5K');
      expect(c.formatCount(999)).toBe('999');
    });
  });

  it('the rendered shell stays backend-neutral (no async/reactive language)', async () => {
    await renderRail();
    expect(document.body.textContent).not.toMatch(/async|reactive|webflux/i);
  });
});
