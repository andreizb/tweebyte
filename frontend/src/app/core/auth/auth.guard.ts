import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { SessionStore } from '../state/session.store';

/**
 * Gate authenticated areas. Edge enforcement is authoritative (gateways 401/403); this
 * guard is purely UX so unauthenticated users land on /login with a returnUrl.
 */
export const authGuard: CanActivateFn = (_route, state): boolean | UrlTree => {
  const session = inject(SessionStore);
  const router = inject(Router);

  if (session.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Inverse guard for /login and /register — bounce signed-in users to the feed. */
export const guestGuard: CanActivateFn = (): boolean | UrlTree => {
  const session = inject(SessionStore);
  const router = inject(Router);
  return session.isAuthenticated() ? router.createUrlTree(['/home']) : true;
};
