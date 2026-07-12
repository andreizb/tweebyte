import { Injectable, signal } from '@angular/core';

/**
 * Tracks the last observed round-trip latency for the active gateway (set by the latency
 * interceptor). Purely cosmetic — surfaced as a badge so an operator gets a felt sense of
 * the active URL's responsiveness. Not keyed by any backend identity and never used for
 * control flow.
 */
@Injectable({ providedIn: 'root' })
export class LatencyService {
  private readonly _lastMs = signal<number | null>(null);
  readonly lastMs = this._lastMs.asReadonly();

  record(ms: number): void {
    this._lastMs.set(Math.round(ms));
  }

  reset(): void {
    this._lastMs.set(null);
  }
}
