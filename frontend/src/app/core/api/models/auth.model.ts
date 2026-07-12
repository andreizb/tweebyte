/**
 * Auth/session models — frontend-side shapes for the decoded JWT and the live session.
 *
 * The bearer comes from POST /auth/login -> { token }. We decode it to read the
 * owner claim (`user_id`, NOT `sub`) and `exp`. Owner-gated routes require the JWT
 * `user_id` claim to equal the {userId} path segment (edge enforces 403).
 */

/** The claims we read out of the access token. */
export interface AccessTokenClaims {
  /** Owner identity used by owner-gated routes. NOT `sub`. */
  user_id?: string;
  /** Keycloak subject — present but not used for ownership. */
  sub?: string;
  /** Preferred username, if the realm includes it. */
  preferred_username?: string;
  email?: string;
  /** Expiry, seconds since epoch. */
  exp?: number;
  /** Issued-at, seconds since epoch. */
  iat?: number;
  [claim: string]: unknown;
}

/** Response of POST /user-service/auth/login and /user-service/auth/register. */
export interface AuthenticationResponse {
  token: string;
}

/** The authenticated session as held in the SessionStore. */
export interface Session {
  token: string;
  userId: string;
  /** Expiry in ms since epoch (derived from `exp * 1000`). */
  expiresAt: number;
  username?: string;
}
