import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { NavRailComponent } from './nav-rail.component';
import { SessionStore } from '../core/state/session.store';

function makeToken(userId: string, username?: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims: Record<string, unknown> = {
    user_id: userId,
    exp: Math.floor(Date.now() / 1000) + 3600
  };
  if (username !== undefined) {
    // `username` is derived from the preferred_username claim (see jwt.util).
    claims['preferred_username'] = username;
  }
  return `${enc({ alg: 'HS256' })}.${enc(claims)}.s`;
}

async function renderRail(
  inputs: Record<string, unknown> = {},
  token: string | null = makeToken('me')
) {
  const result = await render(NavRailComponent, {
    providers: [provideRouter([])],
    componentInputs: inputs,
    configureTestBed: (tb) => {
      if (token) {
        tb.inject(SessionStore).setToken(token);
      }
    }
  });
  result.fixture.detectChanges();
  return result;
}

describe('NavRailComponent', () => {
  beforeEach(() => sessionStorage.clear());

  describe('desktop rail (mobileBar default false)', () => {
    it('renders the full nav: Home/Explore/Search/AI/Settings + Profile + Post', async () => {
      await renderRail();
      for (const label of ['Home', 'Explore', 'Search', 'AI', 'Settings', 'Profile']) {
        expect(screen.getByText(label)).toBeTruthy();
      }
      expect(screen.getByText('Post')).toBeTruthy();
    });
  });

  describe('profilePath()', () => {
    it('routes to /profile/:id when a userId input is set', async () => {
      const { fixture } = await renderRail({ userId: 'u1' });
      expect(fixture.componentInstance.profilePath()).toEqual(['/profile', 'u1']);
    });

    it('falls back to /home when userId is null', async () => {
      const { fixture } = await renderRail({ userId: null });
      expect(fixture.componentInstance.profilePath()).toEqual(['/home']);
    });
  });

  describe('initial()', () => {
    it('is the uppercased first char of the username', async () => {
      const { fixture } = await renderRail({}, makeToken('me', 'ada'));
      expect(fixture.componentInstance.initial()).toBe('A');
    });

    it('falls back to "Y" when there is no session username', async () => {
      const { fixture } = await renderRail({}, null);
      expect(fixture.componentInstance.initial()).toBe('Y');
    });
  });

  describe('mobile bottom bar (mobileBar true)', () => {
    it('renders a nav with a Compose link', async () => {
      await renderRail({ mobileBar: true });
      expect(document.querySelector('nav')).toBeTruthy();
      expect(screen.getByLabelText('Compose')).toBeTruthy();
    });
  });

  describe('logout()', () => {
    it('clears the session (and routes to /login)', async () => {
      const { fixture } = await renderRail();
      const session = fixture.debugElement.injector.get(SessionStore);
      expect(session.isAuthenticated()).toBe(true);

      // jsdom's Location.assign is a non-configurable native accessor: it can't be spied or
      // redefined, and calling it logs an (unimplemented) navigation. None of that affects
      // the behaviour under test — logout() clears the owner identity first — so we just
      // invoke it and assert the session is gone. The try/catch guards the jsdom log path.
      try {
        fixture.componentInstance.logout();
      } catch {
        /* navigation forbidden under jsdom — session.clear() is what we assert below */
      }

      // The owner identity is gone regardless of how navigation is handled.
      expect(session.isAuthenticated()).toBe(false);
    });
  });
});
