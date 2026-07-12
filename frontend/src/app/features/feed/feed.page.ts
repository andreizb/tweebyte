import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  OnInit,
  viewChild
} from '@angular/core';
import { TweetDto } from '../../core/api/models/tweet.model';
import { UserSummaryDto } from '../../core/api/models/user.model';
import { InteractionService } from '../../core/api/services/interaction.service';
import { UserService } from '../../core/api/services/user.service';
import { InteractionStore } from '../../core/state/interaction.store';
import { SessionStore } from '../../core/state/session.store';
import { ViewerStateService } from '../../core/state/viewer-state.service';
import { ComposeBoxComponent } from '../../shared/ui/compose-box/compose-box.component';
import { TweetCardComponent } from '../../shared/ui/tweet-card/tweet-card.component';
import { FeedStore } from './feed.store';

/**
 * M3 home timeline. Loads a paged feed via the FeedStore, hydrates per-tweet interaction
 * counts and backfills the missing tweet authors (each in ONE batched call), supports
 * optimistic compose-insert, and pages in more on scroll via an IntersectionObserver
 * sentinel. Skeleton, empty and error states included.
 *
 * Backend-agnostic: it only talks to the typed API services, which are gateway-relative.
 */
@Component({
  selector: 'tb-feed',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ComposeBoxComponent, TweetCardComponent],
  providers: [FeedStore],
  host: { class: 'block' },
  template: `
    <header class="sticky top-0 z-20 border-b border-border bg-background/80 px-4 py-3 backdrop-blur">
      <h1 class="text-xl font-bold">Home</h1>
    </header>

    <tb-compose-box (posted)="onPosted($event)" />
    <div class="h-2 border-b border-border bg-secondary/20"></div>

    @if (store.loading()) {
      <div class="divide-y divide-border" data-testid="feed-skeletons">
        @for (s of skeletonRows; track s) {
          <div class="flex gap-3 px-4 py-3">
            <div class="skeleton h-11 w-11 rounded-full"></div>
            <div class="flex-1 space-y-2 py-1">
              <div class="skeleton h-3 w-1/3"></div>
              <div class="skeleton h-3 w-5/6"></div>
              <div class="skeleton h-3 w-2/3"></div>
            </div>
          </div>
        }
      </div>
    } @else if (store.error()) {
      <div class="flex flex-col items-center gap-3 px-4 py-16 text-center" role="alert">
        <p class="text-muted-foreground">{{ store.error() }}</p>
        <button
          type="button"
          class="rounded-full bg-brand px-4 py-2 text-sm font-semibold text-brand-foreground"
          (click)="store.load()"
        >
          Try again
        </button>
      </div>
    } @else if (store.isEmpty()) {
      <div class="flex flex-col items-center gap-2 px-4 py-16 text-center" data-testid="feed-empty">
        <h2 class="text-2xl font-bold">Welcome to Tweebyte</h2>
        <p class="max-w-sm text-muted-foreground">
          Your timeline is quiet for now. Follow a few people, or post your first thought above.
        </p>
      </div>
    } @else {
      <ul data-testid="feed-list">
        @for (tweet of store.tweets(); track tweet.id) {
          <li>
            <tb-tweet-card [tweet]="tweet" />
          </li>
        }
      </ul>

      @if (store.loadingMore()) {
        <div class="flex justify-center py-6" data-testid="feed-loading-more">
          <span class="h-6 w-6 animate-spin rounded-full border-2 border-muted-foreground border-r-transparent"></span>
        </div>
      } @else if (store.exhausted()) {
        <p class="py-8 text-center text-sm text-muted-foreground">You're all caught up.</p>
      }

      <!-- Infinite-scroll sentinel -->
      <div #sentinel class="h-px w-full" aria-hidden="true"></div>
    }
  `
})
export class FeedPage implements OnInit {
  readonly store = inject(FeedStore);
  private readonly interactions = inject(InteractionService);
  private readonly users = inject(UserService);
  private readonly interactionStore = inject(InteractionStore);
  private readonly session = inject(SessionStore);
  private readonly viewerState = inject(ViewerStateService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');
  private observer?: IntersectionObserver;

  readonly skeletonRows = Array.from({ length: 6 }, (_, i) => i);

  ngOnInit(): void {
    void this.store.load().then(() => this.hydrate(this.store.tweets()));
  }

  constructor() {
    this.observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          void this.store.loadMore().then(() => this.hydrate(this.store.tweets()));
        }
      },
      { rootMargin: '600px 0px' }
    );
    // The #sentinel lives in the populated @else branch, so it is undefined during the initial
    // loading state — wiring the observer once in ngAfterViewInit attached nothing and broke
    // infinite scroll (loadMore never fired). Observe it REACTIVELY as the viewChild resolves.
    effect(() => {
      const target = this.sentinel()?.nativeElement;
      if (target) {
        this.observer?.observe(target);
      }
    });
    this.destroyRef.onDestroy(() => this.observer?.disconnect());
  }

  onPosted(tweet: TweetDto): void {
    this.store.prepend(tweet);
    // The compose card already carries its author (optimistic), so only counts need hydrating.
    this.hydrateCounts([tweet]);
  }

  /**
   * Hydrate interaction counts, the viewer's liked/retweeted state, and backfill the
   * missing tweet authors for the given tweets.
   */
  private hydrate(tweets: TweetDto[]): void {
    this.hydrateCounts(tweets);
    this.hydrateViewerState(tweets);
    this.hydrateAuthors(tweets);
  }

  /**
   * Light up the like/retweet buttons the viewer already acted on. The feed read carries
   * no viewer-state, so the viewer's own like/retweet sets are pulled once and intersected
   * with the visible ids. Best-effort: a failure leaves the buttons on their default.
   */
  private hydrateViewerState(tweets: TweetDto[]): void {
    const ids = tweets.map((t) => t.id).filter((id): id is string => !!id);
    this.viewerState.hydrateInteractions(this.session.userId(), ids);
  }

  /**
   * GET /tweets/{uid}/feed omits the embedded `user`, so cards would render "Unknown".
   * Collect the distinct author ids that lack a summary, batch-read them in ONE request,
   * and merge each summary back onto its tweets via the store.
   */
  private hydrateAuthors(tweets: TweetDto[]): void {
    const ids = [
      ...new Set(
        tweets
          .filter((t) => !t.user && t.user_id)
          .map((t) => t.user_id as string)
      )
    ];
    if (ids.length === 0) {
      return;
    }
    this.users.summaries(ids).subscribe({
      next: (summaries) => {
        const byUserId = new Map<string, UserSummaryDto>();
        for (const summary of summaries) {
          if (summary.id) {
            byUserId.set(summary.id, summary);
          }
        }
        this.store.mergeAuthors(byUserId);
      },
      error: () => {
        /* cards fall back to their @unknown placeholder; author backfill is best-effort */
      }
    });
  }

  /** One batched call hydrates counts + top reply for the given tweets. */
  private hydrateCounts(tweets: TweetDto[]): void {
    const ids = tweets.map((t) => t.id).filter((id): id is string => !!id);
    if (ids.length === 0) {
      return;
    }
    this.interactions
      .enrich(ids)
      .subscribe({
        next: (rows) => {
          for (const row of rows) {
            this.interactionStore.hydrate(row.tweet_id, {
              likes: row.likes,
              retweets: row.retweets,
              replies: row.replies
            });
          }
        },
        error: () => {
          /* counts already seeded from each card's own DTO; batch is best-effort */
        }
      });
  }
}
