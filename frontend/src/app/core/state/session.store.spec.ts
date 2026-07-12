import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { SessionStore } from './session.store';

function makeToken(payload: Record<string, unknown>): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc(payload)}.s`;
}

describe('SessionStore', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('starts unauthenticated with a clean storage', () => {
    const store = TestBed.inject(SessionStore);
    expect(store.isAuthenticated()).toBe(false);
    expect(store.token()).toBeNull();
    expect(store.userId()).toBeNull();
  });

  it('setToken decodes + persists and flips isAuthenticated', () => {
    const store = TestBed.inject(SessionStore);
    const ok = store.setToken(makeToken({ user_id: 'u1', preferred_username: 'ada', exp: Math.floor(Date.now() / 1000) + 3600 }));
    expect(ok).toBe(true);
    expect(store.isAuthenticated()).toBe(true);
    expect(store.userId()).toBe('u1');
    expect(store.username()).toBe('ada');
    expect(sessionStorage.getItem('tb.session')).toContain('u1');
  });

  it('setToken returns false and does not persist for a bad token', () => {
    const store = TestBed.inject(SessionStore);
    expect(store.setToken('garbage')).toBe(false);
    expect(store.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('tb.session')).toBeNull();
  });

  it('clear wipes state + storage', () => {
    const store = TestBed.inject(SessionStore);
    store.setToken(makeToken({ user_id: 'u1', exp: Math.floor(Date.now() / 1000) + 3600 }));
    store.clear();
    expect(store.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('tb.session')).toBeNull();
  });

  it('setAuthenticating toggles the in-flight flag', () => {
    const store = TestBed.inject(SessionStore);
    store.setAuthenticating(true);
    expect(store.authenticating()).toBe(true);
    store.setAuthenticating(false);
    expect(store.authenticating()).toBe(false);
  });

  it('rehydrates a valid persisted session and rejects an expired one', () => {
    const valid = { token: 't', userId: 'u1', expiresAt: Date.now() + 60_000 };
    sessionStorage.setItem('tb.session', JSON.stringify(valid));
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(true);

    TestBed.resetTestingModule();
    sessionStorage.clear();
    const expired = { token: 't', userId: 'u1', expiresAt: Date.now() - 1 };
    sessionStorage.setItem('tb.session', JSON.stringify(expired));
    TestBed.configureTestingModule({});
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
  });
});
