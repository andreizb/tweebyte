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
import { FormsModule } from '@angular/forms';
import { ReplyDto } from '../../core/api/models/interaction.model';
import { TweetDto } from '../../core/api/models/tweet.model';
import { InteractionService } from '../../core/api/services/interaction.service';
import { TweetService } from '../../core/api/services/tweet.service';
import { InteractionStore } from '../../core/state/interaction.store';
import { SessionStore } from '../../core/state/session.store';
import { ToastService } from '../../core/state/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar/avatar.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { TweetCardComponent } from '../../shared/ui/tweet-card/tweet-card.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { CompactNumberPipe } from '../../shared/pipes/compact-number.pipe';

const MIN_REPLY = 1;

/**
 * Tweet detail / thread. Loads the focal tweet (`/tweets/{id}`) and its replies
 * (`/replies/tweet/{id}`), hydrates interaction counts, and lets the user reply
 * (optimistic insert + reply-count bump). `:id` arrives via component input binding
 * and is tracked so navigating tweet → tweet re-loads (stale in-flight loads ignored).
 */
@Component({
  selector: 'tb-tweet-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    AvatarComponent,
    ButtonComponent,
    TweetCardComponent,
    RelativeTimePipe,
    CompactNumberPipe
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
      <h1 class="text-xl font-bold">Post</h1>
    </header>

    @if (loading()) {
      <div class="flex gap-3 px-4 py-4" data-testid="detail-skeleton">
        <div class="skeleton h-11 w-11 rounded-full"></div>
        <div class="flex-1 space-y-2 py-1">
          <div class="skeleton h-3 w-1/3"></div>
          <div class="skeleton h-4 w-5/6"></div>
        </div>
      </div>
    } @else if (notFound()) {
      <div class="flex flex-col items-center gap-2 px-4 py-16 text-center" data-testid="detail-notfound">
        <h2 class="text-xl font-bold">Post not found</h2>
        <p class="text-muted-foreground">It may have been deleted.</p>
      </div>
    } @else if (tweet()) {
      <tb-tweet-card [tweet]="tweet()!" [clickable]="false" />

      <!-- Reply composer -->
      <div class="flex gap-3 border-b border-border px-4 py-3">
        <tb-avatar [name]="myHandle()" size="md" class="mt-1" />
        <div class="min-w-0 flex-1">
          <textarea
            [(ngModel)]="replyText"
            (input)="onReplyInput()"
            rows="2"
            placeholder="Post your reply"
            class="w-full resize-none border-0 bg-transparent py-2 text-lg outline-none placeholder:text-muted-foreground"
            aria-label="Post your reply"
          ></textarea>
          <div class="flex justify-end">
            <button tbButton variant="brand" size="md" [loading]="posting()" [disabled]="!canReply()" (click)="postReply()">
              Reply
            </button>
          </div>
        </div>
      </div>

      <!-- Thread -->
      <section aria-label="Replies">
        @if (repliesLoading()) {
          <div class="px-4 py-6 text-center" data-testid="replies-loading">
            <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-muted-foreground border-r-transparent"></span>
          </div>
        } @else if (replies().length === 0) {
          <p class="px-4 py-10 text-center text-sm text-muted-foreground" data-testid="replies-empty">
            No replies yet. Be the first to reply.
          </p>
        } @else {
          <ul data-testid="replies-list">
            @for (r of replies(); track r.id) {
              <li class="flex gap-3 border-b border-border px-4 py-3">
                <tb-avatar [name]="r.user_name ?? '?'" size="md" />
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-1.5 text-[15px]">
                    <span class="truncate font-bold">{{ r.user_name }}</span>
                    <span class="truncate text-muted-foreground">&#64;{{ r.user_name }}</span>
                    <span class="text-muted-foreground">·</span>
                    <span class="shrink-0 text-muted-foreground">{{ r.created_at | tbRelativeTime }}</span>
                  </div>
                  <p class="mt-0.5 whitespace-pre-wrap break-words text-[15px]">{{ r.content }}</p>
                  @if (r.likes_count) {
                    <p class="mt-1 text-xs text-muted-foreground">{{ r.likes_count | tbCompactNumber }} likes</p>
                  }
                </div>
              </li>
            }
          </ul>
        }
      </section>
    }
  `
})
export class TweetDetailPage {
  /** Bound from the route param `:id` via withComponentInputBinding(). */
  readonly id = input.required<string>();

  private readonly tweets = inject(TweetService);
  private readonly interactions = inject(InteractionService);
  private readonly interactionStore = inject(InteractionStore);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);
  private readonly location = inject(Location);

  readonly tweet = signal<TweetDto | null>(null);
  readonly replies = signal<ReplyDto[]>([]);
  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly repliesLoading = signal(true);
  readonly posting = signal(false);

  replyText = '';
  readonly replyLen = signal(0);

  readonly myHandle = computed(() => this.session.username() ?? 'you');
  readonly canReply = computed(() => this.replyLen() >= MIN_REPLY && !this.posting());

  /** Bumped on every (re)load so results from a superseded `id` are ignored. */
  private loadToken = 0;

  constructor() {
    // Re-load whenever the route id changes (navigating tweet → tweet).
    effect(
      () => {
        const tweetId = this.id();
        this.reset();
        this.load(tweetId);
      },
      { allowSignalWrites: true }
    );
  }

  private reset(): void {
    this.tweet.set(null);
    this.replies.set([]);
    this.loading.set(true);
    this.notFound.set(false);
    this.repliesLoading.set(true);
    this.replyText = '';
    this.replyLen.set(0);
  }

  private load(tweetId: string): void {
    const token = ++this.loadToken;
    const isStale = () => token !== this.loadToken;

    this.tweets.byId(tweetId).subscribe({
      next: (t) => {
        if (isStale()) {
          return;
        }
        this.tweet.set(t);
        this.loading.set(false);
        if (t.id) {
          this.interactionStore.hydrate(t.id, {
            likes: t.likes_count ?? 0,
            retweets: t.retweets_count ?? 0,
            replies: t.replies_count ?? 0
          });
        }
      },
      error: () => {
        if (isStale()) {
          return;
        }
        this.loading.set(false);
        this.notFound.set(true);
      }
    });

    this.interactions.repliesForTweet(tweetId).subscribe({
      next: (rows) => {
        if (isStale()) {
          return;
        }
        this.replies.set(rows ?? []);
        this.repliesLoading.set(false);
      },
      error: () => {
        if (isStale()) {
          return;
        }
        this.repliesLoading.set(false);
      }
    });
  }

  onReplyInput(): void {
    this.replyLen.set(this.replyText.trim().length);
  }

  postReply(): void {
    const me = this.session.userId();
    const tweetId = this.id();
    const content = this.replyText.trim();
    if (!me || !this.canReply()) {
      return;
    }
    this.posting.set(true);
    this.interactions
      .createReply(me, { tweet_id: tweetId, user_id: me, content })
      .subscribe({
        next: (created) => {
          this.posting.set(false);
          this.replyText = '';
          this.replyLen.set(0);
          // POST /replies returns {id} ONLY — render an optimistic row from local
          // state so the thread shows the author/content/time, not a blank line.
          const optimistic: ReplyDto = {
            id: created.id,
            user_id: me,
            user_name: this.session.username() ?? undefined,
            content,
            created_at: new Date().toISOString(),
            likes_count: 0
          };
          this.replies.update((list) => [optimistic, ...list]);
          this.interactionStore.bumpReplies(tweetId, 1);
          this.toast.success('Reply posted');
        },
        error: () => {
          this.posting.set(false);
          this.toast.error('Could not post reply', 'Please try again.');
        }
      });
  }

  back(): void {
    this.location.back();
  }
}
