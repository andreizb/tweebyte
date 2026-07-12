import { inject, Injectable } from '@angular/core';
import { Observable, from, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { GatewayRegistry } from '../../config/gateway-registry.service';
import { HealthResponse } from '../models/health.model';

export type HealthState = 'up' | 'down' | 'unknown';

/**
 * Pings the active gateway's /actuator/health DIRECTLY (bypassing the base-url
 * interceptor) so the health dot reflects reality without disturbing app requests. Uses
 * raw fetch with a short timeout; never throws. Backend-agnostic — it checks one URL and
 * has no notion of which implementation answers.
 */
@Injectable({ providedIn: 'root' })
export class HealthApiService {
  private readonly registry = inject(GatewayRegistry);

  /** Check the active gateway (default), or a specific gateway by id. */
  check(gatewayId?: string): Observable<HealthState> {
    const descriptor = gatewayId
      ? this.registry.descriptorFor(gatewayId)
      : this.registry.activeDescriptor();
    if (!descriptor) {
      return of<HealthState>('unknown');
    }
    // An empty baseUrl is the same-origin Docker deploy — probe the relative path so the
    // dot still works instead of short-circuiting to 'unknown'.
    const base = gatewayId ? this.registry.baseUrlFor(gatewayId) : this.registry.activeBaseUrl();
    return from(this.ping(`${base}/actuator/health`)).pipe(
      map((ok): HealthState => (ok ? 'up' : 'down')),
      catchError(() => of<HealthState>('down'))
    );
  }

  private async ping(url: string): Promise<boolean> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2500);
    try {
      const res = await fetch(url, { signal: controller.signal, cache: 'no-store' });
      if (!res.ok) {
        return false;
      }
      const body = (await res.json().catch(() => ({}))) as HealthResponse;
      return (body.status ?? 'UP').toUpperCase() === 'UP';
    } catch {
      return false;
    } finally {
      clearTimeout(timer);
    }
  }
}

export { HealthApiService as HealthService };
