import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LogoComponent } from '../shared/ui/logo/logo.component';
import { ButtonComponent } from '../shared/ui/button/button.component';
import { SessionStore } from '../core/state/session.store';

interface NavItem {
  label: string;
  path: string;
  icon: string; // SVG path data (24x24, stroke)
}

const NAV: NavItem[] = [
  { label: 'Home', path: '/home', icon: 'M3 11.5 12 4l9 7.5M5 10v10h5v-6h4v6h5V10' },
  { label: 'Explore', path: '/explore', icon: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM21 21l-4.3-4.3' },
  { label: 'Search', path: '/search', icon: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM21 21l-4.3-4.3' },
  { label: 'AI', path: '/ai', icon: 'M12 3v3m0 12v3M3 12h3m12 0h3M5.6 5.6l2.1 2.1m8.6 8.6 2.1 2.1m0-12.8-2.1 2.1M7.7 16.3 5.6 18.4M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z' },
  { label: 'Settings', path: '/settings', icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM19.4 13a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 7 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H1a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 2.6 7a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H7a1.6 1.6 0 0 0 1-1.5V1a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1 1.6 1.6 0 0 0 .3-1.8l.1-.1' }
];

/**
 * Left navigation rail (≥sm) and the mobile bottom tab bar (<sm, `mobileBar`).
 * Profile path is user-specific; the compose CTA routes to /compose.
 */
@Component({
  selector: 'tb-nav-rail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, LogoComponent, ButtonComponent],
  template: `
    @if (!mobileBar()) {
      <nav class="flex h-full w-full flex-col gap-1 px-2 py-3 xl:items-start">
        <a routerLink="/home" class="mb-2 flex h-12 items-center rounded-full px-3 hover:bg-accent xl:self-start" aria-label="Tweebyte home">
          <tb-logo [showWordmark]="false" class="xl:hidden" />
          <span class="hidden xl:inline"><tb-logo /></span>
        </a>

        @for (item of nav; track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="font-bold"
            #rla="routerLinkActive"
            class="group flex items-center gap-4 rounded-full px-3 py-2.5 text-xl transition-colors hover:bg-accent"
          >
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 [attr.stroke-width]="rla.isActive ? 2.5 : 2" stroke-linecap="round" stroke-linejoin="round" class="shrink-0">
              <path [attr.d]="item.icon" />
            </svg>
            <span class="hidden xl:inline" [class.font-bold]="rla.isActive">{{ item.label }}</span>
          </a>
        }

        <a
          [routerLink]="profilePath()"
          routerLinkActive="font-bold"
          class="group flex items-center gap-4 rounded-full px-3 py-2.5 text-xl transition-colors hover:bg-accent"
        >
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="shrink-0">
            <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM5 21a7 7 0 0 1 14 0" />
          </svg>
          <span class="hidden xl:inline">Profile</span>
        </a>

        <a routerLink="/compose" class="mt-3 w-full">
          <button tbButton variant="brand" size="lg" class="hidden w-full xl:flex">Post</button>
          <button tbButton variant="brand" size="icon" class="flex h-12 w-12 xl:hidden" aria-label="Compose post">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 5v14M5 12h14" />
            </svg>
          </button>
        </a>

        <div class="mt-auto">
          <button
            type="button"
            (click)="logout()"
            class="flex w-full items-center gap-3 rounded-full px-3 py-2.5 text-left transition-colors hover:bg-accent"
            aria-label="Log out"
          >
            <span class="grid h-9 w-9 place-items-center rounded-full bg-secondary text-sm font-bold uppercase">
              {{ initial() }}
            </span>
            <span class="hidden min-w-0 flex-1 xl:block">
              <span class="block truncate text-sm font-semibold">{{ username() ?? 'You' }}</span>
              <span class="block truncate text-xs text-muted-foreground">Log out</span>
            </span>
          </button>
        </div>
      </nav>
    } @else {
      <nav class="fixed inset-x-0 bottom-0 z-40 flex items-center justify-around border-t border-border bg-background/90 px-2 py-2 backdrop-blur">
        @for (item of mobileNav; track item.path) {
          <a [routerLink]="item.path" routerLinkActive="text-foreground" class="rounded-full p-2 text-muted-foreground" [attr.aria-label]="item.label">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path [attr.d]="item.icon" />
            </svg>
          </a>
        }
        <a routerLink="/compose" class="rounded-full bg-brand p-2 text-brand-foreground" aria-label="Compose">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </a>
      </nav>
    }
  `
})
export class NavRailComponent {
  readonly userId = input<string | null>(null);
  readonly mobileBar = input(false);

  private readonly session = inject(SessionStore);
  readonly username = this.session.username;

  readonly nav = NAV;
  readonly mobileNav = NAV.slice(0, 4);

  profilePath(): string[] {
    const id = this.userId();
    return id ? ['/profile', id] : ['/home'];
  }

  initial(): string {
    return (this.username() ?? 'Y').charAt(0).toUpperCase();
  }

  logout(): void {
    this.session.clear();
    location.assign('/login');
  }
}
