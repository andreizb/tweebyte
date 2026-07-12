import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MonoTypeOperatorFunction, throwError, timer } from 'rxjs';
import { catchError, retry } from 'rxjs/operators';
import { SessionStore } from '../state/session.store';
import { ToastService } from '../state/toast.service';

/**
 * Centralized HTTP error handling, per the architecture contract:
 *   401 -> clear session + redirect to /login (no refresh token exists)
 *   403 -> ownership/forbidden toast
 *   404 -> pass through (callers render an empty state)
 *   415 -> unsupported-media toast (multipart mistakes)
 *   429 -> rate-limit toast
 *   5xx -> retry with backoff, then surface
 *
 * Service error bodies are `{ "errors": ["..."] }` (GlobalExceptionHandler); a few endpoints
 * use `{ "error": "..." }`. Both are handled. Streaming (SSE-over-POST via fetch) does NOT
 * pass through HttpClient, so this never interferes with the AI stream.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const session = inject(SessionStore);
  const toast = inject(ToastService);

  return next(req).pipe(
    retryServerErrors(2, req.method),
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        handle(err, { router, session, toast });
      }
      return throwError(() => err);
    })
  );
};

interface Deps {
  router: Router;
  session: InstanceType<typeof SessionStore>;
  toast: ToastService;
}

function handle(err: HttpErrorResponse, { router, session, toast }: Deps): void {
  const detail = extractMessage(err);
  switch (err.status) {
    case 401:
      session.clear();
      void router.navigate(['/login'], {
        queryParams: { returnUrl: router.url }
      });
      break;
    case 403:
      toast.error('Not allowed', detail ?? 'You can only modify your own content.');
      break;
    case 404:
      // Let the caller render an empty state; no global toast.
      break;
    case 415:
      toast.error('Unsupported upload', detail ?? 'That file type was rejected.');
      break;
    case 429:
      toast.warn('Slow down', detail ?? 'Rate limit hit — try again in a moment.');
      break;
    default:
      if (err.status >= 500) {
        toast.error('Something went wrong', detail ?? 'The server had a problem.');
      } else if (err.status === 0) {
        toast.error('Network error', 'Could not reach the backend.');
      }
  }
}

/**
 * Retry 5xx/connection failures with exponential backoff, but ONLY for idempotent methods.
 * Retrying a POST (create tweet/reply/follow) that 5xx'd risks a duplicate write — the first
 * attempt may have committed server-side before the error reached us. GET/HEAD/OPTIONS/PUT/DELETE
 * are idempotent, so a retry has the same effect; POST/PATCH are not, so they surface immediately.
 */
function retryServerErrors<T>(maxRetries: number, method: string): MonoTypeOperatorFunction<T> {
  const idempotent =
    method === 'GET' || method === 'HEAD' || method === 'OPTIONS' || method === 'PUT' || method === 'DELETE';
  return retry<T>({
    count: maxRetries,
    delay: (error, attempt) => {
      const status = error instanceof HttpErrorResponse ? error.status : 0;
      const retriable = idempotent && (status >= 500 || status === 0);
      if (!retriable) {
        return throwError(() => error);
      }
      const backoff = Math.min(1000 * 2 ** (attempt - 1), 4000);
      return timer(backoff);
    }
  });
}

function extractMessage(err: HttpErrorResponse): string | undefined {
  const body = err.error;
  if (body && typeof body === 'object') {
    // GlobalExceptionHandler shape: { "errors": ["..."] } — surface the first message.
    const errors = (body as { errors?: unknown }).errors;
    if (Array.isArray(errors) && typeof errors[0] === 'string' && errors[0].length < 200) {
      return errors[0];
    }
    if (typeof (body as { error?: unknown }).error === 'string') {
      return (body as { error: string }).error;
    }
  }
  if (typeof body === 'string' && body.trim().length > 0 && body.length < 200) {
    return body;
  }
  return undefined;
}
