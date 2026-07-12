import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { FeedStore } from './feed.store';
import { TweetDto } from '../../core/api/models/tweet.model';
import { UserSummaryDto } from '../../core/api/models/user.model';
import { TweetService } from '../../core/api/services/tweet.service';
import { SessionStore } from '../../core/state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

function page(prefix: string, n: number): TweetDto[] {
  return Array.from({ length: n }, (_, i) => ({
    id: `${prefix}-${i}`,
    user_id: 'a',
    content: `c${i}`,
    created_at: new Date().toISOString(),
    media_ids: []
  }));
}

function setup(feed: ReturnType<typeof vi.fn>, authenticated = true) {
  TestBed.configureTestingModule({
    providers: [FeedStore, { provide: TweetService, useValue: { feed } }]
  });
  if (authenticated) {
    TestBed.inject(SessionStore).setToken(makeToken('me'));
  }
  return TestBed.inject(FeedStore);
}

describe('FeedStore', () => {
  beforeEach(() => sessionStorage.clear());

  it('load() fetches the first page and sets exhausted when short', async () => {
    const feed = vi.fn().mockReturnValue(of(page('t', 5))); // < PAGE_SIZE (20)
    const store = setup(feed);
    await store.load();
    expect(store.tweets()).toHaveLength(5);
    expect(store.loading()).toBe(false);
    expect(store.exhausted()).toBe(true);
    expect(feed).toHaveBeenCalledWith('me', 0, 20);
  });

  it('load() with a full page is not exhausted', async () => {
    const store = setup(vi.fn().mockReturnValue(of(page('t', 20))));
    await store.load();
    expect(store.exhausted()).toBe(false);
    expect(store.isEmpty()).toBe(false);
  });

  it('isEmpty() is true after loading nothing', async () => {
    const store = setup(vi.fn().mockReturnValue(of([])));
    await store.load();
    expect(store.isEmpty()).toBe(true);
  });

  it('loadMore() appends the next page and advances the page counter', async () => {
    const feed = vi
      .fn()
      .mockReturnValueOnce(of(page('p0', 20)))
      .mockReturnValueOnce(of(page('p1', 4)));
    const store = setup(feed);
    await store.load();
    await store.loadMore();
    expect(store.tweets()).toHaveLength(24);
    expect(store.page()).toBe(1);
    expect(store.exhausted()).toBe(true);
    expect(feed).toHaveBeenLastCalledWith('me', 1, 20);
  });

  it('loadMore() is a no-op when already exhausted', async () => {
    const feed = vi.fn().mockReturnValue(of(page('p', 3)));
    const store = setup(feed);
    await store.load(); // exhausted (3 < 20)
    await store.loadMore();
    expect(feed).toHaveBeenCalledTimes(1);
  });

  it('prepend() places a tweet at the top', async () => {
    const store = setup(vi.fn().mockReturnValue(of(page('t', 2))));
    await store.load();
    store.prepend({ id: 'fresh', content: 'new', media_ids: [] });
    expect(store.tweets()[0].id).toBe('fresh');
    expect(store.tweets()).toHaveLength(3);
  });

  it('a first-page feed error surfaces the error state (not a silent empty)', async () => {
    const store = setup(vi.fn().mockReturnValue(throwError(() => new Error('boom'))));
    await store.load();
    expect(store.tweets()).toEqual([]);
    expect(store.loading()).toBe(false);
    // The error flag is set; the page renders the error branch (which it checks before the
    // empty branch), so the error — not the empty state — is what the user sees.
    expect(store.error()).toBeTruthy();
  });

  it('Try-again after an error reloads successfully and clears the error', async () => {
    const feed = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new Error('boom')))
      .mockReturnValueOnce(of(page('t', 3)));
    const store = setup(feed);
    await store.load();
    expect(store.error()).toBeTruthy();
    await store.load();
    expect(store.error()).toBeNull();
    expect(store.tweets()).toHaveLength(3);
  });

  it('a loadMore() failure is non-fatal: keeps existing tweets and clears the spinner', async () => {
    const feed = vi
      .fn()
      .mockReturnValueOnce(of(page('p0', 20)))
      .mockReturnValueOnce(throwError(() => new Error('boom')));
    const store = setup(feed);
    await store.load();
    await store.loadMore();
    expect(store.tweets()).toHaveLength(20); // unchanged
    expect(store.loadingMore()).toBe(false);
    expect(store.exhausted()).toBe(false); // can retry on next scroll
  });

  it('load() does nothing without a user id', async () => {
    const feed = vi.fn().mockReturnValue(of(page('t', 2)));
    const store = setup(feed, false); // not authenticated
    await store.load();
    expect(store.tweets()).toEqual([]);
  });

  it('mergeAuthors() backfills the missing author and leaves existing/unknown ones intact', async () => {
    const seeded: TweetDto[] = [
      { id: 't1', user_id: 'a', content: 'c1', media_ids: [] }, // author missing -> filled
      { id: 't2', user_id: 'b', content: 'c2', media_ids: [], user: { id: 'b', user_name: 'kept' } }, // already has one
      { id: 't3', user_id: 'c', content: 'c3', media_ids: [] } // no summary returned -> stays missing
    ];
    const store = setup(vi.fn().mockReturnValue(of(seeded)));
    await store.load();

    const byUserId = new Map<string, UserSummaryDto>([
      ['a', { id: 'a', user_name: 'ada' }],
      ['b', { id: 'b', user_name: 'should-not-overwrite' }]
    ]);
    store.mergeAuthors(byUserId);

    const [t1, t2, t3] = store.tweets();
    expect(t1.user).toEqual({ id: 'a', user_name: 'ada' });
    expect(t2.user).toEqual({ id: 'b', user_name: 'kept' }); // not overwritten
    expect(t3.user).toBeUndefined(); // no summary -> untouched
  });

  it('mergeAuthors() with an empty map is a no-op', async () => {
    const store = setup(vi.fn().mockReturnValue(of(page('t', 2))));
    await store.load();
    const before = store.tweets();
    store.mergeAuthors(new Map());
    expect(store.tweets()).toBe(before); // same reference: no patch issued
  });
});
