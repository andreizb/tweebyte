import { Injectable, signal } from '@angular/core';

export type ToastKind = 'info' | 'success' | 'error' | 'warn';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  /** Optional secondary line. */
  detail?: string;
}

/**
 * Minimal app-wide toast bus. Components subscribe to {@link toasts} (a signal) and
 * render them; the ErrorInterceptor and feature flows push via the typed helpers.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  private push(kind: ToastKind, message: string, detail?: string, ttlMs = 4500): void {
    const id = ++this.seq;
    this._toasts.update((list) => [...list, { id, kind, message, detail }]);
    if (ttlMs > 0) {
      setTimeout(() => this.dismiss(id), ttlMs);
    }
  }

  info(message: string, detail?: string): void {
    this.push('info', message, detail);
  }
  success(message: string, detail?: string): void {
    this.push('success', message, detail);
  }
  warn(message: string, detail?: string): void {
    this.push('warn', message, detail);
  }
  error(message: string, detail?: string): void {
    this.push('error', message, detail, 6000);
  }

  dismiss(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
