import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { TweetDto } from '../../core/api/models/tweet.model';
import { UserSummaryDto } from '../../core/api/models/user.model';
import { TweetService } from '../../core/api/services/tweet.service';
import { SessionStore } from '../../core/state/session.store';

interface FeedState {
  tweets: TweetDto[];
  page: number;
  size: number;
  loading: boolean;
  loadingMore: boolean;
  exhausted: boolean;
  error: string | null;
}

const PAGE_SIZE = 20;

/**
 * Home-timeline store: paged load, infinite scroll, optimistic compose-insert.
 * RxX flows live in the page; this store owns the materialized list + flags.
 */
export const FeedStore = signalStore(
  withState<FeedState>({
    tweets: [],
    page: 0,
    size: PAGE_SIZE,
    loading: false,
    loadingMore: false,
    exhausted: false,
    error: null
  }),
  withComputed(({ tweets, loading }) => ({
    isEmpty: computed(() => !loading() && tweets().length === 0)
  })),
  withMethods((store) => {
    const tweetsApi = inject(TweetService);
    const session = inject(SessionStore);

    // Resolves to the page rows, or null to signal the request FAILED (distinct from an
    // empty-but-successful page, so the page can show its error/retry state vs the empty
    // state). A missing session id is treated as an empty success.
    const fetchPage = (page: number): Promise<TweetDto[] | null> => {
      const uid = session.userId();
      if (!uid) {
        return Promise.resolve([]);
      }
      return new Promise((resolve) => {
        tweetsApi.feed(uid, page, store.size()).subscribe({
          next: (rows) => resolve(rows ?? []),
          error: () => resolve(null)
        });
      });
    };

    return {
      async load(): Promise<void> {
        if (store.loading()) {
          return;
        }
        patchState(store, { loading: true, error: null, page: 0, exhausted: false });
        const rows = await fetchPage(0);
        if (rows === null) {
          patchState(store, {
            loading: false,
            error: "Couldn't load your timeline."
          });
          return;
        }
        patchState(store, {
          tweets: rows,
          loading: false,
          exhausted: rows.length < store.size()
        });
      },
      async loadMore(): Promise<void> {
        if (store.loadingMore() || store.loading() || store.exhausted()) {
          return;
        }
        patchState(store, { loadingMore: true });
        const next = store.page() + 1;
        const rows = await fetchPage(next);
        if (rows === null) {
          // Pagination failure is non-fatal: keep what we have, stop the spinner, and let
          // a later scroll retry. Don't mark exhausted (so the sentinel can fire again).
          patchState(store, { loadingMore: false });
          return;
        }
        patchState(store, {
          tweets: [...store.tweets(), ...rows],
          page: next,
          loadingMore: false,
          exhausted: rows.length < store.size()
        });
      },
      /** Optimistically place a freshly-composed tweet at the top. */
      prepend(tweet: TweetDto): void {
        patchState(store, { tweets: [tweet, ...store.tweets()] });
      },
      /**
       * Backfill the embedded author on cards that arrived without one. GET /tweets/{uid}/feed
       * returns tweets with no `user`, so the page batch-reads the missing authors and hands
       * the summaries here keyed by user_id. Cards that already carry a `user` (e.g. an
       * optimistic compose insert) are left untouched.
       */
      mergeAuthors(byUserId: Map<string, UserSummaryDto>): void {
        if (byUserId.size === 0) {
          return;
        }
        patchState(store, {
          tweets: store.tweets().map((t) => {
            if (t.user || !t.user_id) {
              return t;
            }
            const summary = byUserId.get(t.user_id);
            return summary ? { ...t, user: summary } : t;
          })
        });
      }
    };
  })
);
