import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs/operators';
import { LatencyService } from '../state/latency.service';

/**
 * Records round-trip latency for the active gateway so the selector can show a live badge.
 * Cosmetic only and backend-agnostic — it times whichever URL is active without caring
 * what serves it. Local asset/config fetches stay relative (base-url leaves them alone),
 * so they don't reach here.
 */
export const latencyInterceptor: HttpInterceptorFn = (req, next) => {
  const latency = inject(LatencyService);
  const started = performance.now();

  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        latency.record(performance.now() - started);
      }
    })
  );
};
