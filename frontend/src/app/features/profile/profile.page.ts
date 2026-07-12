import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { Location } from '@angular/common';
import { FollowCountsDto } from '../../core/api/models/follow.model';
import { TweetDto } from '../../core/api/models/tweet.model';
import { UserDto, UserSummaryDto } from '../../core/api/models/user.model';
import { FollowService } from '../../core/api/services/follow.service';
import { TweetService } from '../../core/api/services/tweet.service';
import { UserService } from '../../core/api/services/user.service';
import { InteractionStore } from '../../core/state/interaction.store';
import { SessionStore } from '../../core/state/session.store';
import { ViewerStateService } from '../../core/state/viewer-state.service';
import { AvatarComponent } from '../../shared/ui/avatar/avatar.component';
import { FollowButtonComponent } from '../../shared/ui/follow-button/follow-button.component';
import { TweetCardComponent } from '../../shared/ui/tweet-card/tweet-card.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { CompactNumberPipe } from '../../shared/pipes/compact-number.pipe';
import { EditProfileDialogComponent } from './edit-profile.dialog';

/**
 * M4 profile. Loads the user (`/users/{id}`) and their tweets (`/tweets/user/{id}`),
 * then the profile one-shot (`/follows/{id}/profile-interactions`) to hydrate follow
 * counts + per-tweet interaction counts in a single round-trip. The tweet list arrives
 * without an embedded author, so a single batched `/users/summaries` read backfills it.
 * The FollowButton is hidden on the viewer's own profile (owner awareness). `:id` arrives
 * via input binding.
 */
@Component({
  selector: 'tb-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AvatarComponent,
    FollowButtonComponent,
    TweetCardComponent,
    RelativeTimePipe,
    CompactNumberPipe,
    EditProfileDialogComponent
  ],
  host: { class: 'block' },
  template: `
    <header class="sticky top-0 z-20 flex items-center gap-4 border-b border-border bg-background/80 px-4 py-3 backdrop-blur">
      <button
        type="button"
        class="grid h-9 w-9 place-items-center rounded-full hover:bg-accent"
        (click)="back()"
        aria-label="Back"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <h1 class="truncate text-xl font-bold">{{ user()?.user_name ?? 'Profile' }}</h1>
    </header>

    @if (user(); as u) {
      <section class="border-b border-border px-4 py-4">
        <div class="flex items-start justify-between">
          <tb-avatar [mediaId]="u.profile_picture_id" [name]="u.user_name ?? '?'" size="lg" />
          @if (!isMe()) {
            <tb-follow-button
              [meId]="meId()"
              [targetId]="u.id ?? ''"
              [isPrivate]="!!u.is_private"
              [initial]="followsTarget() ? 'following' : 'idle'"
            />
          } @else {
            <button
              type="button"
              class="rounded-full border border-border px-4 py-1.5 text-sm font-semibold hover:bg-accent"
              (click)="editing.set(true)"
            >
              Edit profile
            </button>
          }
        </div>

        <h2 class="mt-3 text-xl font-extrabold">{{ u.user_name }}</h2>
        <p class="text-sm text-muted-foreground">&#64;{{ u.user_name }}</p>

        @if (u.biography) {
          <p class="mt-2 whitespace-pre-wrap text-[15px]">{{ u.biography }}</p>
        }

        <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
          @if (u.is_private) {
            <span class="inline-flex items-center gap-1">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
              Private
            </span>
          }
          @if (u.created_at) {
            <span>Joined {{ u.created_at | tbRelativeTime }}</span>
          }
        </div>

        <div class="mt-3 flex gap-5 text-sm">
          <span><span class="font-bold text-foreground">{{ following() | tbCompactNumber: false }}</span>
            <span class="text-muted-foreground">Following</span></span>
          <span><span class="font-bold text-foreground">{{ followers() | tbCompactNumber: false }}</span>
            <span class="text-muted-foreground">Followers</span></span>
        </div>
      </section>

      <!-- Tweets -->
      @if (tweetsLoading()) {
        <div class="px-4 py-6 text-center" data-testid="profile-tweets-loading">
          <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-muted-foreground border-r-transparent"></span>
        </div>
      } @else if (tweets().length === 0) {
        <p class="px-4 py-10 text-center text-sm text-muted-foreground" data-testid="profile-tweets-empty">
          @if (isMe()) {
            You haven't posted yet.
          } @else {
            &#64;{{ u.user_name }} hasn't posted yet.
          }
        </p>
      } @else {
        <ul data-testid="profile-tweets">
          @for (t of tweets(); track t.id) {
            <li><tb-tweet-card [tweet]="t" /></li>
          }
        </ul>
      }
    } @else if (loading()) {
      <div class="px-4 py-6" data-testid="profile-skeleton">
        <div class="skeleton h-20 w-20 rounded-full"></div>
        <div class="mt-4 space-y-2">
          <div class="skeleton h-4 w-1/3"></div>
          <div class="skeleton h-3 w-2/3"></div>
        </div>
      </div>
    } @else {
      <div class="flex flex-col items-center gap-2 px-4 py-16 text-center" data-testid="profile-notfound">
        <h2 class="text-xl font-bold">This account doesn't exist</h2>
        <p class="text-muted-foreground">Try searching for another.</p>
      </div>
    }

    @if (editing() && user(); as u) {
      <tb-edit-profile-dialog
        [user]="u"
        (saved)="onSaved($event)"
        (close)="editing.set(false)"
      />
    }
  `
})
export class ProfilePage {
  /** Bound from the route param `:id`. */
  readonly id = input.required<string>();

  private readonly users = inject(UserService);
  private readonly tweetsApi = inject(TweetService);
  private readonly follows = inject(FollowService);
  private readonly interactionStore = inject(InteractionStore);
  private readonly session = inject(SessionStore);
  private readonly viewerState = inject(ViewerStateService);
  private readonly location = inject(Location);

  readonly user = signal<UserDto | null>(null);
  readonly tweets = signal<TweetDto[]>([]);
  readonly loading = signal(true);
  readonly tweetsLoading = signal(true);
  readonly editing = signal(false);
  /** True once the viewer's followed-id set is known to contain this profile. */
  readonly followsTarget = signal(false);

  private readonly counts = signal<FollowCountsDto | null>(null);

  readonly meId = this.session.userId;
  readonly isMe = computed(() => this.session.userId() === this.id());
  readonly followers = computed(() => this.counts()?.followers ?? this.user()?.followers ?? 0);
  readonly following = computed(() => this.counts()?.following ?? this.user()?.following ?? 0);

  constructor() {
    // Re-load whenever the route id changes (navigating profile → profile).
    effect(
      () => {
        const userId = this.id();
        this.reset();
        this.load(userId);
      },
      { allowSignalWrites: true }
    );
  }

  private reset(): void {
    this.user.set(null);
    this.tweets.set([]);
    this.counts.set(null);
    this.loading.set(true);
    this.tweetsLoading.set(true);
    this.editing.set(false);
    this.followsTarget.set(false);
  }

  private load(userId: string): void {
    this.users.get(userId).subscribe({
      next: (u) => {
        this.user.set(u);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });

    this.tweetsApi.byUser(userId, 0, 20, true).subscribe({
      next: (rows) => {
        const list = rows ?? [];
        this.tweets.set(list);
        this.tweetsLoading.set(false);
        this.hydrateProfile(userId, list);
        this.hydrateViewerState(list);
        this.hydrateAuthors(list);
      },
      error: () => this.tweetsLoading.set(false)
    });

    this.loadFollowState(userId);
  }

  /**
   * Seed the header follow-button: read the viewer's followed-id set once and flip the
   * button to `following` when it already contains this profile. Skipped on the viewer's
   * own profile (no button) and best-effort (an empty/failed set leaves it on `idle`).
   */
  private loadFollowState(userId: string): void {
    const me = this.session.userId();
    if (!me || me === userId) {
      return;
    }
    this.viewerState.followedIds(me).subscribe((followed) => {
      this.followsTarget.set(followed.has(userId));
    });
  }

  /**
   * Light up the like/retweet buttons the viewer already acted on. The profile-tweets read
   * carries no viewer-state, so the viewer's own like/retweet sets are pulled once and
   * intersected with the visible ids. Best-effort: a failure leaves the buttons unlit.
   */
  private hydrateViewerState(tweets: TweetDto[]): void {
    const ids = tweets.map((t) => t.id).filter((id): id is string => !!id);
    this.viewerState.hydrateInteractions(this.session.userId(), ids);
  }

  /**
   * GET /tweets/user/{id} omits the embedded `user`, so cards would render "Unknown".
   * Collect the distinct author ids that lack a summary, batch-read them in ONE request,
   * and merge each summary back onto its tweets.
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
        this.tweets.update((list) =>
          list.map((t) => {
            if (t.user || !t.user_id) {
              return t;
            }
            const summary = byUserId.get(t.user_id);
            return summary ? { ...t, user: summary } : t;
          })
        );
      },
      error: () => {
        /* cards fall back to their @unknown placeholder; author backfill is best-effort */
      }
    });
  }

  /** Profile one-shot: counts + batched interaction rows for this profile's tweets. */
  private hydrateProfile(userId: string, tweets: TweetDto[]): void {
    const ids = tweets.map((t) => t.id).filter((tid): tid is string => !!tid);
    this.follows.profileInteractions(userId, ids).subscribe({
      next: (res) => {
        if (res.follow_counts) {
          this.counts.set(res.follow_counts);
        }
        for (const row of res.tweet_interactions ?? []) {
          this.interactionStore.hydrate(row.tweet_id, {
            likes: row.likes,
            retweets: row.retweets,
            replies: row.replies
          });
        }
      },
      error: () => {
        /* counts fall back to the user DTO; best-effort */
      }
    });
  }

  /** Apply the saved profile in place and close the dialog. */
  onSaved(updated: UserDto): void {
    this.user.update((current) => ({ ...current, ...updated }));
    this.editing.set(false);
  }

  back(): void {
    this.location.back();
  }
}
