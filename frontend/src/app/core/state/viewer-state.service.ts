import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of } from 'rxjs';

import { FollowService } from '../api/services/follow.service';
import { InteractionService } from '../api/services/interaction.service';
import { InteractionStore } from './interaction.store';

/**
 * Generous single-page bound for the viewer's own like/retweet/followed reads. The
 * read DTOs carry no viewer-state (the lean benchmark contract), so the UI resolves
 * button state client-side by pulling the viewer's OWN interaction sets once and
 * intersecting them with the visible list. These endpoints are paginated; one large
 * page keeps it to a single round-trip per set rather than walking pages on every load.
 */
const VIEWER_SET_SIZE = 10000;

/**
 * Resolves viewer-state for the like/retweet/follow buttons without bloating the
 * benchmarked read DTOs. After a feed/profile list loads, callers ask this service to
 * hydrate the {@link InteractionStore} liked/retweeted flags for the tweets the viewer
 * already acted on, and to read the viewer's followed-id set so follow-buttons can render
 * `following` instead of `idle`.
 *
 * Every fetch is best-effort: a failure leaves the buttons on their default and never
 * breaks the page. Callers fetch the viewer sets ONCE per feed/profile load (not per tweet).
 */
@Injectable({ providedIn: 'root' })
export class ViewerStateService {
  private readonly interactions = inject(InteractionService);
  private readonly follows = inject(FollowService);
  private readonly store = inject(InteractionStore);

  /**
   * Pull the viewer's liked + retweeted tweet ids once and flip the matching cards'
   * optimistic flags on. `hydrate` preserves existing counts and never clobbers a flag
   * the user just toggled, so this is safe to call after every (re)load. Best-effort:
   * a failed set simply leaves those buttons unlit.
   *
   * @param viewerId the current user (no-op when signed out)
   * @param visibleTweetIds ids on screen; only these are hydrated to bound the work
   */
  hydrateInteractions(viewerId: string | null, visibleTweetIds: readonly string[]): void {
    if (!viewerId || visibleTweetIds.length === 0) {
      return;
    }
    const visible = new Set(visibleTweetIds);

    this.interactions
      .userLikes(viewerId, 0, VIEWER_SET_SIZE)
      .pipe(catchError(() => of([])))
      .subscribe((likes) => {
        for (const like of likes) {
          const id = like.tweet?.id;
          if (id && visible.has(id)) {
            this.store.hydrate(id, {}, { liked: true });
          }
        }
      });

    this.interactions
      .retweetsByUser(viewerId, 0, VIEWER_SET_SIZE)
      .pipe(catchError(() => of([])))
      .subscribe((retweets) => {
        for (const rt of retweets) {
          const id = rt.tweet?.id;
          if (id && visible.has(id)) {
            // Carry the retweet id so a later un-repost can DELETE it instead of skipping.
            this.store.hydrate(id, {}, { retweeted: true });
            if (rt.id) {
              this.store.setRetweetId(id, rt.id);
            }
          }
        }
      });
  }

  /**
   * The set of user ids the viewer follows (ACCEPTED edges). Used to seed follow-buttons
   * with `[initial]="'following'"`. Resolves to an empty set when signed out or on error
   * (best-effort). Callers read this once per load.
   *
   * NOTE: despite the path, GET /follows/{id}/followers/identifiers returns the ids the
   * user FOLLOWS, not their followers (server-side route-name drift) — exactly the set we
   * want here.
   */
  followedIds(viewerId: string | null): Observable<ReadonlySet<string>> {
    if (!viewerId) {
      return of(new Set<string>());
    }
    return this.follows.followersIdentifiers(viewerId).pipe(
      map((ids) => new Set(ids) as ReadonlySet<string>),
      catchError(() => of(new Set<string>() as ReadonlySet<string>))
    );
  }
}
