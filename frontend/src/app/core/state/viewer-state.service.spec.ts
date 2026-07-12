import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ViewerStateService } from './viewer-state.service';
import { InteractionStore } from './interaction.store';
import { InteractionService } from '../api/services/interaction.service';
import { FollowService } from '../api/services/follow.service';

interface InterMock {
  userLikes?: ReturnType<typeof vi.fn>;
  retweetsByUser?: ReturnType<typeof vi.fn>;
}

interface FollowMock {
  followersIdentifiers?: ReturnType<typeof vi.fn>;
}

function setup(inter: InterMock = {}, follow: FollowMock = {}) {
  const interSvc = {
    userLikes: inter.userLikes ?? vi.fn().mockReturnValue(of([])),
    retweetsByUser: inter.retweetsByUser ?? vi.fn().mockReturnValue(of([]))
  };
  const followSvc = {
    followersIdentifiers: follow.followersIdentifiers ?? vi.fn().mockReturnValue(of([]))
  };
  TestBed.configureTestingModule({
    providers: [
      { provide: InteractionService, useValue: interSvc },
      { provide: FollowService, useValue: followSvc }
    ]
  });
  const service = TestBed.inject(ViewerStateService);
  const store = TestBed.inject(InteractionStore);
  return { service, store, interSvc, followSvc };
}

describe('ViewerStateService', () => {
  beforeEach(() => sessionStorage.clear());

  describe('hydrateInteractions', () => {
    it("flips the liked/retweeted flags for visible ids in the viewer's sets", () => {
      const userLikes = vi.fn().mockReturnValue(of([{ id: 'l1', tweet: { id: 't1' } }]));
      const retweetsByUser = vi.fn().mockReturnValue(of([{ id: 'rt2', tweet: { id: 't2' } }]));
      const { service, store } = setup({ userLikes, retweetsByUser });

      service.hydrateInteractions('me', ['t1', 't2']);

      expect(userLikes).toHaveBeenCalledWith('me', 0, 10000);
      expect(retweetsByUser).toHaveBeenCalledWith('me', 0, 10000);
      expect(store.snapshot()('t1').liked).toBe(true);
      expect(store.snapshot()('t2').retweeted).toBe(true);
      // The retweet id is carried so a later un-repost can DELETE the right edge.
      expect(store.snapshot()('t2').retweetId).toBe('rt2');
    });

    it('ignores liked/retweeted ids that are not in the visible set', () => {
      const userLikes = vi.fn().mockReturnValue(of([{ id: 'l1', tweet: { id: 'other' } }]));
      const retweetsByUser = vi.fn().mockReturnValue(of([{ id: 'rt2', tweet: { id: 'other' } }]));
      const { service, store } = setup({ userLikes, retweetsByUser });

      service.hydrateInteractions('me', ['t1']);

      expect(store.snapshot()('other').liked).toBe(false);
      expect(store.snapshot()('other').retweeted).toBe(false);
    });

    it('skips like/retweet rows without a tweet id', () => {
      const userLikes = vi.fn().mockReturnValue(of([{ id: 'l1' }]));
      const retweetsByUser = vi.fn().mockReturnValue(of([{ id: 'rt2' }]));
      const { service, store } = setup({ userLikes, retweetsByUser });

      service.hydrateInteractions('me', ['t1']);

      expect(store.snapshot()('t1').liked).toBe(false);
      expect(store.snapshot()('t1').retweeted).toBe(false);
    });

    it('does not set a retweet id when the retweet row omits its own id', () => {
      const retweetsByUser = vi.fn().mockReturnValue(of([{ tweet: { id: 't1' } }]));
      const { service, store } = setup({ retweetsByUser });

      service.hydrateInteractions('me', ['t1']);

      expect(store.snapshot()('t1').retweeted).toBe(true);
      expect(store.snapshot()('t1').retweetId).toBeNull();
    });

    it('is a no-op when signed out', () => {
      const { service, interSvc } = setup();
      service.hydrateInteractions(null, ['t1']);
      expect(interSvc.userLikes).not.toHaveBeenCalled();
      expect(interSvc.retweetsByUser).not.toHaveBeenCalled();
    });

    it('is a no-op when there are no visible ids', () => {
      const { service, interSvc } = setup();
      service.hydrateInteractions('me', []);
      expect(interSvc.userLikes).not.toHaveBeenCalled();
    });

    it('swallows a failing set and leaves the flags on their default', () => {
      const userLikes = vi.fn().mockReturnValue(throwError(() => new Error('x')));
      const retweetsByUser = vi.fn().mockReturnValue(throwError(() => new Error('x')));
      const { service, store } = setup({ userLikes, retweetsByUser });

      expect(() => service.hydrateInteractions('me', ['t1'])).not.toThrow();
      expect(store.snapshot()('t1').liked).toBe(false);
      expect(store.snapshot()('t1').retweeted).toBe(false);
    });

    it('preserves a flag the user already toggled (hydrate does not clobber)', () => {
      const userLikes = vi.fn().mockReturnValue(of([]));
      const { service, store } = setup({ userLikes });
      store.setLiked('t1', true); // user liked optimistically; the empty set must not unset it

      service.hydrateInteractions('me', ['t1']);

      expect(store.snapshot()('t1').liked).toBe(true);
    });
  });

  describe('followedIds', () => {
    it('resolves the followed-id set from the (route-drifted) identifiers endpoint', async () => {
      const followersIdentifiers = vi.fn().mockReturnValue(of(['a', 'b']));
      const { service, followSvc } = setup({}, { followersIdentifiers });

      const set = await firstValue(service.followedIds('me'));

      expect(followSvc.followersIdentifiers).toHaveBeenCalledWith('me');
      expect(set.has('a')).toBe(true);
      expect(set.has('b')).toBe(true);
      expect(set.has('c')).toBe(false);
    });

    it('returns an empty set when signed out (no request)', async () => {
      const { service, followSvc } = setup();
      const set = await firstValue(service.followedIds(null));
      expect(set.size).toBe(0);
      expect(followSvc.followersIdentifiers).not.toHaveBeenCalled();
    });

    it('returns an empty set (best-effort) when the read fails', async () => {
      const followersIdentifiers = vi.fn().mockReturnValue(throwError(() => new Error('x')));
      const { service } = setup({}, { followersIdentifiers });
      const set = await firstValue(service.followedIds('me'));
      expect(set.size).toBe(0);
    });

    it('reads the set fresh on each call (fetched once per load by callers)', async () => {
      const followersIdentifiers = vi
        .fn()
        .mockReturnValueOnce(of(['a']))
        .mockReturnValueOnce(of(['a', 'b']));
      const { service, followSvc } = setup({}, { followersIdentifiers });

      const first = await firstValue(service.followedIds('me'));
      const second = await firstValue(service.followedIds('me'));

      expect(followSvc.followersIdentifiers).toHaveBeenCalledTimes(2);
      expect(first.has('b')).toBe(false);
      expect(second.has('b')).toBe(true);
    });
  });
});

/** Resolve the first emission of an observable as a promise (synchronous sources land at once). */
function firstValue<T>(obs: { subscribe: (o: (v: T) => void) => void }): Promise<T> {
  return new Promise<T>((resolve) => obs.subscribe((v) => resolve(v)));
}
