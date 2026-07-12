import { delay, HttpResponse } from 'msw';

/**
 * Test-only scenario overrides for the MSW mock backend.
 *
 * The mock normally serves the happy path. E2E specs that need to exercise error and
 * edge states (429 rate-limit, 5xx, 401, forced-empty collections, induced latency)
 * cannot use Playwright's `page.route` for these endpoints: MSW runs as a Service Worker
 * and fulfils the request before Playwright's network layer sees it. So instead the
 * handlers consult this small, mutable scenario registry, and specs drive it through the
 * `window.__tbMock` hook (set via `page.evaluate`).
 *
 * This is dev/test plumbing only — it is tree-shaken out of production builds along with
 * the rest of `src/mocks`, and the registry is empty (pure happy path) unless a test
 * opts in. It never changes the app's real request/response shapes.
 */

/**
 * A forced failure. `error` overrides the JSON `{ error }` message; `errors` instead emits
 * the GlobalExceptionHandler shape `{ errors: [...] }` (the interceptor surfaces errors[0]).
 * `text` emits a JSON string response body. `status: 0` is a transport-level network error
 * (no response).
 */
export type FailKind =
  | {
      status: 400 | 401 | 403 | 404 | 415 | 429 | 500 | 503;
      error?: string;
      errors?: string[];
      text?: string;
    }
  | { status: 0 };

interface Override {
  /** Force every matching call to fail with this response. */
  fail?: FailKind;
  /** Fail only the next N matching calls, then fall through to the happy path. */
  failTimes?: number;
  /** Return an empty collection / null for matching reads. */
  empty?: boolean;
  /** Return a literal JSON null for matching reads. */
  nullBody?: boolean;
  /** Add this many ms of artificial latency before responding. */
  latencyMs?: number;
}

/**
 * Endpoint groups a test can target. Kept coarse and intent-revealing rather than 1:1
 * with routes, so specs read like behaviour ("make the feed fail") not plumbing.
 */
export type ScenarioKey =
  | 'feed'
  | 'compose'
  | 'reply'
  | 'trends'
  | 'recommendations'
  | 'like'
  | 'retweet'
  | 'follow'
  | 'followed-ids'
  | 'health'
  | 'search-users'
  | 'search-tweets'
  | 'summaries'
  | 'viewer-likes'
  | 'viewer-retweets'
  | 'profile'
  | 'profile-tweets'
  | 'profile-interactions'
  | 'profile-update'
  | 'login'
  | 'register'
  | 'media';

const overrides = new Map<ScenarioKey, Override>();

/**
 * Hydrate overrides from the URL so a scenario can be active for the VERY FIRST request
 * after a navigation/reload (before any `page.evaluate` could run). Recognised params:
 *   ?__fail=feed:500            force the feed to fail with 500
 *   ?__fail=feed:500:1          fail only the next 1 call, then recover
 *   ?__fail=like:429,follow:0   multiple groups, comma-separated (0 = network error)
 *   ?__empty=feed,search-users  force these reads to return empty collections
 *   ?__null=feed,reply          force these reads to return literal JSON null
 * Test-only; ignored when no such params are present (the production happy path).
 */
function hydrateFromUrl(): void {
  if (typeof location === 'undefined') {
    return;
  }
  const params = new URLSearchParams(location.search);
  const fail = params.get('__fail');
  if (fail) {
    for (const spec of fail.split(',')) {
      const [key, status, times] = spec.split(':');
      if (key && status) {
        tbMock.fail(
          key as ScenarioKey,
          { status: Number(status) } as FailKind,
          times ? Number(times) : undefined
        );
      }
    }
  }
  const empty = params.get('__empty');
  if (empty) {
    for (const key of empty.split(',')) {
      if (key) {
        tbMock.empty(key as ScenarioKey);
      }
    }
  }
  const nulls = params.get('__null');
  if (nulls) {
    for (const key of nulls.split(',')) {
      if (key) {
        tbMock.nullBody(key as ScenarioKey);
      }
    }
  }
}

export interface TbMockHook {
  /** Force a group of endpoints to fail (or to fail only the next N calls). */
  fail(key: ScenarioKey, kind: FailKind, times?: number): void;
  /** Force matching reads to return an empty collection. */
  empty(key: ScenarioKey): void;
  /** Force matching reads to return literal JSON null. */
  nullBody(key: ScenarioKey): void;
  /** Add artificial latency to a group of endpoints. */
  slow(key: ScenarioKey, ms: number): void;
  /** Clear one override, or all of them when called with no argument. */
  reset(key?: ScenarioKey): void;
}

export const tbMock: TbMockHook = {
  fail(key, kind, times) {
    overrides.set(key, { ...(overrides.get(key) ?? {}), fail: kind, failTimes: times });
  },
  empty(key) {
    overrides.set(key, { ...(overrides.get(key) ?? {}), empty: true });
  },
  nullBody(key) {
    overrides.set(key, { ...(overrides.get(key) ?? {}), nullBody: true });
  },
  slow(key, ms) {
    overrides.set(key, { ...(overrides.get(key) ?? {}), latencyMs: ms });
  },
  reset(key) {
    if (key) {
      overrides.delete(key);
    } else {
      overrides.clear();
    }
  }
};

/** Whether a forced-empty override is active for this group. */
export function isEmpty(key: ScenarioKey): boolean {
  return overrides.get(key)?.empty === true;
}

/** Whether a literal-null override is active for this group. */
export function isNull(key: ScenarioKey): boolean {
  return overrides.get(key)?.nullBody === true;
}

/**
 * If an override demands this group fail, returns the error HttpResponse (or a thrown
 * network error) and decrements any one-shot counter. Returns null on the happy path.
 * Also applies any configured latency as a side effect.
 */
export async function maybeFail(key: ScenarioKey): Promise<Response | null> {
  const o = overrides.get(key);
  if (!o) {
    return null;
  }
  if (o.latencyMs) {
    await delay(o.latencyMs);
  }
  if (!o.fail) {
    return null;
  }
  if (typeof o.failTimes === 'number') {
    if (o.failTimes <= 0) {
      return null;
    }
    o.failTimes -= 1;
  }
  if (o.fail.status === 0) {
    // Simulate a connection failure (HttpClient maps this to status 0).
    return HttpResponse.error();
  }
  if (o.fail.text !== undefined) {
    return HttpResponse.json(o.fail.text, { status: o.fail.status });
  }
  // GlobalExceptionHandler shape `{ errors: [...] }` when asked, else the simpler
  // `{ error }` shape — both are what the error interceptor's extractMessage handles.
  const body =
    o.fail.errors !== undefined
      ? { errors: o.fail.errors }
      : { error: o.fail.error ?? defaultMessage(o.fail.status) };
  return HttpResponse.json(body, { status: o.fail.status });
}

function defaultMessage(status: number): string {
  switch (status) {
    case 400:
      return 'Bad request';
    case 401:
      return 'Unauthorized';
    case 403:
      return 'Forbidden';
    case 404:
      return 'Not found';
    case 415:
      return 'Unsupported media type';
    case 429:
      return 'Too many requests';
    case 500:
      return 'Internal server error';
    case 503:
      return 'Service unavailable';
    default:
      return 'Error';
  }
}

// Apply any URL-driven overrides as soon as this module loads (before the first request).
hydrateFromUrl();
