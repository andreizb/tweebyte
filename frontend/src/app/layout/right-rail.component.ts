import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { TweetService } from '../core/api/services/tweet.service';
import { RecommendationService } from '../core/api/services/recommendation.service';
import { SessionStore } from '../core/state/session.store';
import { ViewerStateService } from '../core/state/viewer-state.service';
import { GatewaySelectorComponent } from './gateway-selector.component';
import { FollowButtonComponent } from '../shared/ui/follow-button/follow-button.component';

/**
 * Right rail: the connection panel (neutral gateway selector), trends, and who-to-follow.
 * Data is best-effort; failures collapse to empty cards rather than erroring the shell.
 */
@Component({
  selector: 'tb-right-rail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, GatewaySelectorComponent, FollowButtonComponent],
  template: `
    <div class="flex flex-col gap-4 pb-10">
      <tb-gateway-selector />

      <!-- Trends -->
      <section class="overflow-hidden rounded-2xl border border-border bg-secondary/30">
        <h2 class="px-4 pt-3 text-lg font-bold">Trends</h2>
        <ul>
          @for (t of trends(); track t.text) {
            <li>
              <a
                [routerLink]="['/explore']"
                [queryParams]="{ tag: t.text }"
                class="block px-4 py-2.5 transition-colors hover:bg-accent"
              >
                <p class="text-xs text-muted-foreground">Trending</p>
                <p class="font-semibold">#{{ t.text }}</p>
                @if (t.count) {
                  <p class="text-xs text-muted-foreground">{{ formatCount(t.count) }} posts</p>
                }
              </a>
            </li>
          } @empty {
            @for (s of [1, 2, 3]; track s) {
              <li class="px-4 py-3"><div class="skeleton h-4 w-2/3"></div></li>
            }
          }
        </ul>
      </section>

      <!-- Who to follow -->
      <section class="overflow-hidden rounded-2xl border border-border bg-secondary/30">
        <h2 class="px-4 pt-3 text-lg font-bold">Who to follow</h2>
        <ul>
          @for (u of suggestions(); track u.id) {
            <li class="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-accent">
              <a [routerLink]="['/profile', u.id]" class="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-secondary text-sm font-bold uppercase">
                {{ (u.user_name ?? '?').charAt(0) }}
              </a>
              <a [routerLink]="['/profile', u.id]" class="min-w-0 flex-1">
                <p class="truncate text-sm font-semibold">{{ u.user_name }}</p>
                <p class="truncate text-xs text-muted-foreground">&#64;{{ u.user_name }}</p>
              </a>
              <tb-follow-button
                [meId]="meId()"
                [targetId]="u.id ?? ''"
                [initial]="followedIds().has(u.id ?? '') ? 'following' : 'idle'"
                size="sm"
              />
            </li>
          } @empty {
            @for (s of [1, 2]; track s) {
              <li class="px-4 py-3"><div class="skeleton h-9 w-full"></div></li>
            }
          }
        </ul>
      </section>

      <footer class="px-4 text-xs leading-relaxed text-muted-foreground">
        Tweebyte © 2026 · About · Help · Terms · Privacy
      </footer>
    </div>
  `
})
export class RightRailComponent {
  private readonly tweets = inject(TweetService);
  private readonly recs = inject(RecommendationService);
  private readonly session = inject(SessionStore);
  private readonly viewerState = inject(ViewerStateService);
  private readonly router = inject(Router);

  readonly meId = this.session.userId;

  readonly trends = toSignal(
    this.tweets.popularHashtags().pipe(catchError(() => of([]))),
    { initialValue: [] }
  );

  readonly suggestions = toSignal(
    (this.session.userId()
      ? this.recs.whoToFollow(this.session.userId()!)
      : of([])
    ).pipe(catchError(() => of([]))),
    { initialValue: [] }
  );

  /**
   * Ids the viewer already follows, so a suggested account that is already followed renders
   * `Following` rather than `Follow`. Read once via the shared viewer-state cache; empty
   * until it resolves and on any failure (best-effort).
   */
  readonly followedIds = toSignal(this.viewerState.followedIds(this.session.userId()), {
    initialValue: new Set<string>() as ReadonlySet<string>
  });

  formatCount(n: number): string {
    if (n >= 1_000_000) {
      return `${(n / 1_000_000).toFixed(1)}M`;
    }
    if (n >= 1_000) {
      return `${(n / 1_000).toFixed(1)}K`;
    }
    return String(n);
  }
}
