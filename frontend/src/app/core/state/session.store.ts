import { computed } from '@angular/core';
import { signalStore, withComputed, withMethods, withState, patchState } from '@ngrx/signals';
import { Session } from '../api/models/auth.model';
import { isExpired, sessionFromToken } from '../auth/jwt.util';

const SESSION_STORAGE_KEY = 'tb.session';

interface SessionState {
  session: Session | null;
  /** Set true while a login/register request is in flight. */
  authenticating: boolean;
}

function readPersisted(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Session;
    return isExpired(parsed) ? null : parsed;
  } catch {
    return null;
  }
}

function persist(session: Session | null): void {
  try {
    if (session) {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
    }
  } catch {
    /* storage unavailable — non-fatal */
  }
}

/**
 * The one source of truth for auth. Holds the bearer + decoded owner identity,
 * mirrors it to sessionStorage, and survives a backend swap (the toggle only changes
 * base URL, never this store).
 */
export const SessionStore = signalStore(
  { providedIn: 'root' },
  withState<SessionState>(() => ({
    session: readPersisted(),
    authenticating: false
  })),
  withComputed(({ session }) => ({
    isAuthenticated: computed(() => session() !== null && !isExpired(session())),
    token: computed(() => session()?.token ?? null),
    userId: computed(() => session()?.userId ?? null),
    username: computed(() => session()?.username ?? null)
  })),
  withMethods((store) => ({
    /** Accept a fresh `{ token }` from login/register; decode + persist. */
    setToken(token: string): boolean {
      const session = sessionFromToken(token);
      if (!session) {
        return false;
      }
      patchState(store, { session, authenticating: false });
      persist(session);
      return true;
    },
    setAuthenticating(value: boolean): void {
      patchState(store, { authenticating: value });
    },
    /** Clear on logout or 401. */
    clear(): void {
      patchState(store, { session: null, authenticating: false });
      persist(null);
    }
  }))
);
