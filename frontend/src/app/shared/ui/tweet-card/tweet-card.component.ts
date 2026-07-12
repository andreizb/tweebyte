import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { TweetDto } from '../../../core/api/models/tweet.model';
import { InteractionService } from '../../../core/api/services/interaction.service';
import { InteractionStore } from '../../../core/state/interaction.store';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../core/state/toast.service';
import { AvatarComponent } from '../avatar/avatar.component';
import { MediaThumbComponent } from '../media-thumb/media-thumb.component';
import { RelativeTimePipe } from '../../pipes/relative-time.pipe';
import { CompactNumberPipe } from '../../pipes/compact-number.pipe';

/**
 * The canonical tweet card — maps a tweet-service TweetDto 1:1, no extra fan-out.
 * Like/retweet are optimistic via InteractionStore (instant flip, rollback on error).
 * Content is linkified for #hashtags and @mentions. Reply/quote routes to detail.
 */
@Component({
  selector: 'tb-tweet-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, AvatarComponent, MediaThumbComponent, RelativeTimePipe, CompactNumberPipe],
  host: { class: 'block' },
  template: `
    <article
      class="flex gap-3 border-b border-border px-4 py-3 transition-colors hover:bg-accent/30"
      [class.cursor-pointer]="clickable()"
    >
      <a [routerLink]="['/profile', tweet().user_id]" class="shrink-0" (click)="$event.stopPropagation()">
        <tb-avatar [mediaId]="avatarId()" [name]="handle()" size="md" />
      </a>

      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1.5 text-[15px]">
          <a [routerLink]="['/profile', tweet().user_id]" class="truncate font-bold hover:underline" (click)="$event.stopPropagation()">
            {{ displayName() }}
          </a>
          <span class="truncate text-muted-foreground">&#64;{{ handle() }}</span>
          <span class="text-muted-foreground">·</span>
          <a [routerLink]="['/tweet', tweet().id]" class="shrink-0 text-muted-foreground hover:underline" (click)="$event.stopPropagation()">
            {{ tweet().created_at | tbRelativeTime }}
          </a>
        </div>

        <a [routerLink]="['/tweet', tweet().id]" class="mt-0.5 block whitespace-pre-wrap break-words text-[15px] leading-normal" (click)="$event.stopPropagation()">
          <span [innerHTML]="renderedContent()"></span>
        </a>

        @if (mediaIds().length) {
          <div class="mt-2 grid gap-2 overflow-hidden rounded-2xl border border-border" [class.grid-cols-2]="mediaIds().length > 1">
            @for (mid of mediaIds(); track mid) {
              <tb-media-thumb [mediaId]="mid" />
            }
          </div>
        }

        <!-- Action bar -->
        <div class="mt-2 flex max-w-md items-center justify-between text-muted-foreground">
          <a [routerLink]="['/tweet', tweet().id]" class="group flex items-center gap-1.5" (click)="$event.stopPropagation()" aria-label="Reply">
            <span class="grid h-9 w-9 place-items-center rounded-full transition-colors group-hover:bg-reply/10 group-hover:text-reply">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.4 8.4 0 0 1-8.5 8.5 8.6 8.6 0 0 1-3.9-.9L3 21l1.9-5.6A8.4 8.4 0 0 1 12.5 3 8.4 8.4 0 0 1 21 11.5Z" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
            <span class="text-xs tabular-nums">{{ state().replies | tbCompactNumber }}</span>
          </a>

          <button type="button" class="group flex items-center gap-1.5" (click)="toggleRetweet($event)" [attr.aria-pressed]="state().retweeted" aria-label="Retweet">
            <span class="grid h-9 w-9 place-items-center rounded-full transition-colors group-hover:bg-retweet/10 group-hover:text-retweet" [class.text-retweet]="state().retweeted">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M7 7h11l-3-3M17 17H6l3 3" stroke-linecap="round" stroke-linejoin="round"/><path d="M18 7v6M6 17v-6" stroke-linecap="round"/></svg>
            </span>
            <span class="text-xs tabular-nums" [class.text-retweet]="state().retweeted">{{ state().retweets | tbCompactNumber }}</span>
          </button>

          <button type="button" class="group flex items-center gap-1.5" (click)="toggleLike($event)" [attr.aria-pressed]="state().liked" aria-label="Like">
            <span class="grid h-9 w-9 place-items-center rounded-full transition-colors group-hover:bg-like/10 group-hover:text-like" [class.text-like]="state().liked" [class.animate-like-burst]="burst()">
              <svg width="18" height="18" viewBox="0 0 24 24" [attr.fill]="state().liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2"><path d="M12 21s-7.5-4.6-9.6-9A5 5 0 0 1 12 6a5 5 0 0 1 9.6 6c-2.1 4.4-9.6 9-9.6 9Z" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
            <span class="text-xs tabular-nums" [class.text-like]="state().liked">{{ state().likes | tbCompactNumber }}</span>
          </button>

          <span class="grid h-9 w-9 place-items-center rounded-full transition-colors hover:bg-brand/10 hover:text-brand" aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v13" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </span>
        </div>
      </div>
    </article>
  `
})
export class TweetCardComponent {
  readonly tweet = input.required<TweetDto>();
  readonly clickable = input(true);

  private readonly interactions = inject(InteractionService);
  private readonly store = inject(InteractionStore);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  readonly burst = signal(false);

  /** Tweet ids whose DTO counts we have already seeded, so author-backfill re-fires don't reseed. */
  private readonly seeded = new Set<string>();

  readonly state = computed(() => this.store.snapshot()(this.tweet().id ?? ''));

  readonly handle = computed(() => this.tweet().user?.user_name ?? 'unknown');
  readonly displayName = computed(() => this.tweet().user?.user_name ?? 'Unknown');
  readonly avatarId = computed(() => undefined); // user summary carries no picture id; profile fills it
  readonly mediaIds = computed(() => this.tweet().media_ids ?? []);

  readonly renderedContent = computed(() => linkify(this.tweet().content ?? ''));

  constructor() {
    // Seed the store from the card's own DTO counts once per tweet id. Author-backfill
    // (feed.store mergeAuthors / profile hydrateAuthors) swaps the tweet object reference,
    // which re-fires this effect; reseeding would overwrite an optimistic like/retweet count
    // with the stale DTO value, so we skip ids we have already seeded.
    effect(
      () => {
        const t = this.tweet();
        if (!t.id || this.seeded.has(t.id)) {
          return;
        }
        this.seeded.add(t.id);
        this.store.hydrate(t.id, {
          likes: t.likes_count ?? 0,
          retweets: t.retweets_count ?? 0,
          replies: t.replies_count ?? 0
        });
      },
      { allowSignalWrites: true }
    );
  }

  toggleLike(ev: Event): void {
    ev.stopPropagation();
    const me = this.session.userId();
    const id = this.tweet().id;
    if (!me || !id) {
      return;
    }
    const next = !this.state().liked;
    this.store.setLiked(id, next);
    if (next) {
      this.burst.set(true);
      setTimeout(() => this.burst.set(false), 420);
    }
    const call$: Observable<unknown> = next
      ? this.interactions.likeTweet(me, id)
      : this.interactions.unlikeTweet(me, id);
    call$.subscribe({
      error: () => {
        this.store.setLiked(id, !next); // rollback
        this.toast.error('Could not update like');
      }
    });
  }

  toggleRetweet(ev: Event): void {
    ev.stopPropagation();
    const me = this.session.userId();
    const id = this.tweet().id;
    if (!me || !id) {
      return;
    }
    const wasRetweeted = this.state().retweeted;
    const knownRetweetId = this.state().retweetId;
    const next = !wasRetweeted;
    this.store.setRetweeted(id, next);

    if (next) {
      // Repost: create, then remember the new retweet id so a later "off" can DELETE it.
      this.interactions.createRetweet(me, { original_tweet_id: id, retweeter_id: me }).subscribe({
        next: (rt) => this.store.setRetweetId(id, rt.id ?? null),
        error: () => {
          this.store.setRetweeted(id, false);
          this.toast.error('Could not repost');
        }
      });
      return;
    }

    // Un-repost: DELETE the tracked retweet. If we never learned the id (e.g. server-seeded
    // repost), skip the call but keep the optimistic flip rather than wrongly creating one.
    if (!knownRetweetId) {
      return;
    }
    this.interactions.deleteRetweet(me, knownRetweetId).subscribe({
      error: () => {
        this.store.setRetweeted(id, true, knownRetweetId); // rollback (restore id)
        this.toast.error('Could not remove repost');
      }
    });
  }
}

/** Linkify #hashtags and @mentions with safe escaping. */
function linkify(text: string): string {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  return escaped
    .replace(/(^|\s)#([\p{L}0-9_]+)/gu, '$1<span class="text-brand">#$2</span>')
    .replace(/(^|\s)@([\p{L}0-9_]+)/gu, '$1<span class="text-brand">@$2</span>');
}
