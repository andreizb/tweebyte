import { jwtDecode } from 'jwt-decode';
import { AccessTokenClaims, Session } from '../api/models/auth.model';

/**
 * Decode a Tweebyte access token into a Session.
 *
 * The owner identity is the `user_id` claim (NOT `sub`) — owner-gated routes compare
 * `user_id` to the {userId} path segment. Returns null for malformed tokens or tokens
 * with no usable owner id.
 */
export function sessionFromToken(token: string): Session | null {
  try {
    const claims = jwtDecode<AccessTokenClaims>(token);
    const userId = claims.user_id ?? claims.sub;
    if (!userId) {
      return null;
    }
    return {
      token,
      userId,
      expiresAt: typeof claims.exp === 'number' ? claims.exp * 1000 : Number.MAX_SAFE_INTEGER,
      username: claims.preferred_username
    };
  } catch {
    return null;
  }
}

/** True if the session is absent or past its expiry (small skew tolerance). */
export function isExpired(session: Session | null, skewMs = 5_000): boolean {
  if (!session) {
    return true;
  }
  return Date.now() + skewMs >= session.expiresAt;
}
