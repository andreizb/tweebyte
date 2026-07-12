import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { SessionStore } from '../state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

const route = {} as ActivatedRouteSnapshot;
const state = { url: '/home' } as RouterStateSnapshot;

describe('authGuard / guestGuard', () => {
  let session: InstanceType<typeof SessionStore>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    session = TestBed.inject(SessionStore);
  });

  it('authGuard allows an authenticated user', () => {
    session.setToken(makeToken('u1'));
    expect(TestBed.runInInjectionContext(() => authGuard(route, state))).toBe(true);
  });

  it('authGuard redirects an anonymous user to /login with returnUrl', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(route, state)) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    const url = TestBed.inject(Router).serializeUrl(result);
    expect(url).toContain('/login');
    expect(url).toContain('returnUrl');
  });

  it('guestGuard bounces an authenticated user to /home', () => {
    session.setToken(makeToken('u1'));
    const result = TestBed.runInInjectionContext(() => guestGuard(route, state)) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result)).toContain('/home');
  });

  it('guestGuard allows an anonymous user', () => {
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state))).toBe(true);
  });
});
