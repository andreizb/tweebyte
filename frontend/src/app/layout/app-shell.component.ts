import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavRailComponent } from './nav-rail.component';
import { RightRailComponent } from './right-rail.component';
import { SessionStore } from '../core/state/session.store';

/**
 * The 3-column X shell:
 *   left  — sticky nav rail (icons → labels at xl), brand, compose CTA, account chip
 *   center— the routed feed/timeline column, capped ~600px, the only scroll region
 *   right — sticky rail: search, trends, who-to-follow, and the connection panel
 *
 * Collapses gracefully: right rail hides < lg, nav rail condenses to icons < xl,
 * and a bottom tab bar takes over on mobile (handled inside NavRail).
 */
@Component({
  selector: 'tb-app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, NavRailComponent, RightRailComponent],
  host: { class: 'block min-h-screen bg-background text-foreground' },
  template: `
    <div class="mx-auto flex w-full max-w-[1290px] justify-center">
      <!-- Left nav rail -->
      <header
        class="sticky top-0 z-30 hidden h-screen shrink-0 sm:flex sm:w-[88px] xl:w-[275px]"
      >
        <tb-nav-rail [userId]="userId()" />
      </header>

      <!-- Center column -->
      <main
        class="min-h-screen w-full max-w-[600px] shrink-0 border-x border-border"
        role="main"
      >
        <router-outlet />
      </main>

      <!-- Right rail -->
      <aside class="sticky top-0 hidden h-screen w-[350px] shrink-0 overflow-y-auto px-6 py-3 lg:block">
        <tb-right-rail />
      </aside>
    </div>

    <!-- Mobile bottom tab bar -->
    <tb-nav-rail class="sm:hidden" [userId]="userId()" [mobileBar]="true" />
  `
})
export class AppShellComponent {
  private readonly session = inject(SessionStore);
  readonly userId = this.session.userId;
}
