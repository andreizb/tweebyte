import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Split-screen auth layout: a branded hero panel (left, ≥lg) and the projected form
 * (right). Pure presentation; both auth pages reuse it. Backend-agnostic — the copy says
 * nothing about what serves the app.
 */
@Component({
  selector: 'tb-auth-split',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-h-screen' },
  template: `
    <div class="grid min-h-screen lg:grid-cols-2">
      <!-- Hero -->
      <div class="relative hidden overflow-hidden lg:block">
        <div class="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,hsl(var(--brand)/0.28),transparent_55%),radial-gradient(ellipse_at_bottom_right,hsl(var(--brand)/0.14),transparent_55%)]"></div>
        <div class="absolute inset-0 opacity-[0.05]" style="background-image:linear-gradient(hsl(var(--foreground)) 1px,transparent 1px),linear-gradient(90deg,hsl(var(--foreground)) 1px,transparent 1px);background-size:42px 42px"></div>
        <div class="relative z-10 flex h-full flex-col justify-between p-12">
          <svg width="44" height="44" viewBox="0 0 32 32" fill="none" aria-hidden="true">
            <path d="M9 8 L15 16 L9 24 M14 8 L20 16 L14 24 M19 8 L25 16 L19 24" stroke="hsl(var(--brand))" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <div class="max-w-md">
            <h2 class="text-4xl font-bold leading-tight tracking-tight">
              Happening now.<br />
              Join <span class="text-brand">Tweebyte</span> today.
            </h2>
            <p class="mt-4 text-base text-muted-foreground">
              Follow the people and topics you care about, share what's on your mind, and
              keep up with the conversation in real time.
            </p>
            <ul class="mt-6 flex flex-col gap-3 text-sm text-muted-foreground">
              <li class="flex items-center gap-2">
                <span class="grid h-6 w-6 place-items-center rounded-full bg-brand/15 text-brand">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6 9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                A clean, fast home timeline
              </li>
              <li class="flex items-center gap-2">
                <span class="grid h-6 w-6 place-items-center rounded-full bg-brand/15 text-brand">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6 9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                Reply, repost, like — instantly
              </li>
              <li class="flex items-center gap-2">
                <span class="grid h-6 w-6 place-items-center rounded-full bg-brand/15 text-brand">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6 9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                Trends and people worth following
              </li>
            </ul>
          </div>
          <p class="text-xs text-muted-foreground">© Tweebyte</p>
        </div>
      </div>

      <!-- Form -->
      <div class="flex items-center justify-center p-6 sm:p-12">
        <ng-content />
      </div>
    </div>
  `
})
export class AuthSplitComponent {}
