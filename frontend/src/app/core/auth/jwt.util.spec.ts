import { describe, expect, it } from 'vitest';
import { isExpired, sessionFromToken } from './jwt.util';

/** Build an unsigned but structurally-valid JWT with the given payload. */
function makeToken(payload: Record<string, unknown>): string {
  const enc = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc(payload)}.sig`;
}

describe('sessionFromToken', () => {
  it('decodes user_id, exp, and preferred_username into a Session', () => {
    const exp = Math.floor(Date.now() / 1000) + 3600;
    const token = makeToken({ user_id: 'u-1', preferred_username: 'ada', exp });
    const session = sessionFromToken(token);
    expect(session).not.toBeNull();
    expect(session!.userId).toBe('u-1');
    expect(session!.username).toBe('ada');
    expect(session!.expiresAt).toBe(exp * 1000);
    expect(session!.token).toBe(token);
  });

  it('falls back to sub when user_id is absent', () => {
    const token = makeToken({ sub: 'subject-9', exp: Math.floor(Date.now() / 1000) + 60 });
    expect(sessionFromToken(token)!.userId).toBe('subject-9');
  });

  it('prefers the local user_id claim over the opaque subject', () => {
    const token = makeToken({ user_id: 'local-user', sub: 'keycloak-subject' });
    expect(sessionFromToken(token)!.userId).toBe('local-user');
  });

  it('returns null when neither user_id nor sub is present', () => {
    const token = makeToken({ preferred_username: 'nobody' });
    expect(sessionFromToken(token)).toBeNull();
  });

  it('returns null for a malformed token', () => {
    expect(sessionFromToken('not-a-jwt')).toBeNull();
  });

  it('uses MAX_SAFE_INTEGER when exp is missing', () => {
    const token = makeToken({ user_id: 'u-2' });
    expect(sessionFromToken(token)!.expiresAt).toBe(Number.MAX_SAFE_INTEGER);
  });
});

describe('isExpired', () => {
  it('treats a null session as expired', () => {
    expect(isExpired(null)).toBe(true);
  });

  it('is false for a session expiring well in the future', () => {
    expect(isExpired({ token: 't', userId: 'u', expiresAt: Date.now() + 60_000 })).toBe(false);
  });

  it('is true for an already-past expiry', () => {
    expect(isExpired({ token: 't', userId: 'u', expiresAt: Date.now() - 1 })).toBe(true);
  });

  it('respects the skew window (about-to-expire counts as expired)', () => {
    expect(
      isExpired({ token: 't', userId: 'u', expiresAt: Date.now() + 1_000 }, 5_000)
    ).toBe(true);
  });

  it('allows callers to disable the skew window', () => {
    expect(isExpired({ token: 't', userId: 'u', expiresAt: Date.now() + 1_000 }, 0)).toBe(false);
  });
});
