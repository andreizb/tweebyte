import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, ToastKind } from '../../../core/state/toast.service';

const KIND_CLASSES: Record<ToastKind, string> = {
  info: 'border-border bg-popover',
  success: 'border-retweet/40 bg-popover',
  warn: 'border-warn/50 bg-popover',
  error: 'border-destructive/50 bg-popover'
};

const KIND_DOT: Record<ToastKind, string> = {
  info: 'bg-brand',
  success: 'bg-retweet',
  warn: 'bg-warn',
  error: 'bg-destructive'
};

/** Bottom-right toast stack. Accessible live region; auto-dismiss handled by the service. */
@Component({
  selector: 'tb-toast-host',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pointer-events-none fixed bottom-4 right-4 z-[100] flex w-[min(360px,calc(100vw-2rem))] flex-col gap-2' },
  template: `
    <div aria-live="polite" aria-atomic="false" class="contents">
      @for (t of toasts.toasts(); track t.id) {
        <div
          class="pointer-events-auto flex items-start gap-3 rounded-xl border p-3 shadow-lg backdrop-blur animate-fade-in"
          [class]="kindClass(t.kind)"
          role="status"
        >
          <span class="mt-1.5 h-2 w-2 shrink-0 rounded-full" [class]="dotClass(t.kind)"></span>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-semibold text-foreground">{{ t.message }}</p>
            @if (t.detail) {
              <p class="mt-0.5 text-xs text-muted-foreground">{{ t.detail }}</p>
            }
          </div>
          <button
            type="button"
            class="rounded-md p-1 text-muted-foreground hover:bg-accent hover:text-foreground"
            (click)="toasts.dismiss(t.id)"
            aria-label="Dismiss notification"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6 6 18M6 6l12 12" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      }
    </div>
  `
})
export class ToastHostComponent {
  readonly toasts = inject(ToastService);
  kindClass(k: ToastKind): string {
    return KIND_CLASSES[k];
  }
  dotClass(k: ToastKind): string {
    return KIND_DOT[k];
  }
}
