import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';

/** Per-tweet interaction snapshot held client-side for O(1) optimistic flips. */
export interface TweetInteractionState {
  likes: number;
  retweets: number;
  replies: number;
  liked: boolean;
  retweeted: boolean;
  /**
   * Id of the current user's retweet of this tweet, when known. DELETE /retweets needs
   * it, so toggling a repost "off" can actually remove it instead of creating another.
   */
  retweetId: string | null;
}

interface InteractionState {
  byId: Record<string, TweetInteractionState>;
}

const empty = (): TweetInteractionState => ({
  likes: 0,
  retweets: 0,
  replies: 0,
  liked: false,
  retweeted: false,
  retweetId: null
});

/**
 * Optimistic interaction store keyed by tweetId. The TweetCard reads/writes here so a
 * like/retweet flips instantly and rolls back on API failure, independent of which
 * backend is active. Hydrated from feed counts + the batched enrichment call.
 */
export const InteractionStore = signalStore(
  { providedIn: 'root' },
  withState<InteractionState>({ byId: {} }),
  withComputed(({ byId }) => ({
    /** Returns a selector for a single tweet's state (or empty). */
    snapshot: computed(() => (id: string): TweetInteractionState => byId()[id] ?? empty())
  })),
  withMethods((store) => ({
    /** Seed/refresh a tweet's counts (does not clobber optimistic liked/retweeted). */
    hydrate(
      id: string,
      counts: Partial<Pick<TweetInteractionState, 'likes' | 'retweets' | 'replies'>>,
      flags?: Partial<Pick<TweetInteractionState, 'liked' | 'retweeted'>>
    ): void {
      patchState(store, (state) => ({
        byId: { ...state.byId, [id]: { ...(state.byId[id] ?? empty()), ...counts, ...flags } }
      }));
    },
    setLiked(id: string, liked: boolean): void {
      patchState(store, (state) => {
        const prev = state.byId[id] ?? empty();
        const delta = liked === prev.liked ? 0 : liked ? 1 : -1;
        return {
          byId: {
            ...state.byId,
            [id]: { ...prev, liked, likes: Math.max(0, prev.likes + delta) }
          }
        };
      });
    },
    /**
     * Flip the optimistic retweet state. When reposting, pass the new retweet id (or set
     * it later via {@link setRetweetId}); when un-reposting, the stored id is cleared.
     */
    setRetweeted(id: string, retweeted: boolean, retweetId?: string | null): void {
      patchState(store, (state) => {
        const prev = state.byId[id] ?? empty();
        const delta = retweeted === prev.retweeted ? 0 : retweeted ? 1 : -1;
        return {
          byId: {
            ...state.byId,
            [id]: {
              ...prev,
              retweeted,
              retweetId: retweeted ? (retweetId ?? prev.retweetId) : null,
              retweets: Math.max(0, prev.retweets + delta)
            }
          }
        };
      });
    },
    /** Record the retweet id once the create call resolves (for a later DELETE). */
    setRetweetId(id: string, retweetId: string | null): void {
      patchState(store, (state) => ({
        byId: { ...state.byId, [id]: { ...(state.byId[id] ?? empty()), retweetId } }
      }));
    },
    bumpReplies(id: string, by = 1): void {
      patchState(store, (state) => {
        const prev = state.byId[id] ?? empty();
        return {
          byId: { ...state.byId, [id]: { ...prev, replies: Math.max(0, prev.replies + by) } }
        };
      });
    }
  }))
);
