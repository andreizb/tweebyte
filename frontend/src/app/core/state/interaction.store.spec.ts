import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { InteractionStore } from './interaction.store';

describe('InteractionStore', () => {
  let store: InstanceType<typeof InteractionStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(InteractionStore);
  });

  it('snapshot returns an empty default for unknown tweets', () => {
    expect(store.snapshot()('ghost')).toEqual({
      likes: 0,
      retweets: 0,
      replies: 0,
      liked: false,
      retweeted: false,
      retweetId: null
    });
  });

  it('hydrate seeds counts without clobbering optimistic flags', () => {
    store.setLiked('t1', true); // liked, likes -> 1
    store.hydrate('t1', { likes: 10, retweets: 4, replies: 2 });
    const s = store.snapshot()('t1');
    expect(s.likes).toBe(10);
    expect(s.retweets).toBe(4);
    expect(s.liked).toBe(true); // preserved
  });

  it('hydrate can set flags when explicitly passed', () => {
    store.hydrate('t1', { likes: 3 }, { liked: true, retweeted: true });
    const s = store.snapshot()('t1');
    expect(s.liked).toBe(true);
    expect(s.retweeted).toBe(true);
  });

  it('hydrate preserves omitted counts from the existing snapshot', () => {
    store.hydrate('t1', { likes: 5, retweets: 4, replies: 3 });
    store.hydrate('t1', { likes: 8 });
    expect(store.snapshot()('t1')).toMatchObject({ likes: 8, retweets: 4, replies: 3 });
  });

  it('setLiked toggles like and adjusts the count by one each way', () => {
    store.hydrate('t1', { likes: 5 });
    store.setLiked('t1', true);
    expect(store.snapshot()('t1').likes).toBe(6);
    store.setLiked('t1', false);
    expect(store.snapshot()('t1').likes).toBe(5);
  });

  it('setLiked is idempotent when state is unchanged (no double-count)', () => {
    store.hydrate('t1', { likes: 5 });
    store.setLiked('t1', true);
    store.setLiked('t1', true);
    expect(store.snapshot()('t1').likes).toBe(6);
  });

  it('like count never goes below zero', () => {
    store.setLiked('t1', false); // from empty (0, not liked)
    expect(store.snapshot()('t1').likes).toBe(0);
  });

  it('setRetweeted toggles retweet and adjusts the count', () => {
    store.hydrate('t1', { retweets: 2 });
    store.setRetweeted('t1', true);
    expect(store.snapshot()('t1').retweets).toBe(3);
    store.setRetweeted('t1', false);
    expect(store.snapshot()('t1').retweets).toBe(2);
  });

  it('setRetweeted is idempotent when state is unchanged', () => {
    store.hydrate('t1', { retweets: 2 });
    store.setRetweeted('t1', true, 'rt-1');
    store.setRetweeted('t1', true);
    expect(store.snapshot()('t1')).toMatchObject({ retweeted: true, retweets: 3, retweetId: 'rt-1' });
  });

  it('retweet count never goes below zero', () => {
    store.setRetweeted('t1', false);
    expect(store.snapshot()('t1').retweets).toBe(0);
  });

  it('tracks the retweet id when reposting and clears it when un-reposting', () => {
    store.setRetweeted('t1', true, 'rt-123');
    expect(store.snapshot()('t1').retweetId).toBe('rt-123');
    store.setRetweeted('t1', false);
    expect(store.snapshot()('t1').retweetId).toBeNull();
  });

  it('setRetweetId records the id after the create call resolves', () => {
    store.setRetweeted('t1', true); // optimistic, id not yet known
    expect(store.snapshot()('t1').retweetId).toBeNull();
    store.setRetweetId('t1', 'rt-late');
    expect(store.snapshot()('t1').retweetId).toBe('rt-late');
  });

  it('setRetweeted(true) without an id preserves a previously-known id', () => {
    store.setRetweetId('t1', 'rt-existing');
    store.setRetweeted('t1', true);
    expect(store.snapshot()('t1').retweetId).toBe('rt-existing');
  });

  it('bumpReplies adds/removes and clamps at zero', () => {
    store.hydrate('t1', { replies: 1 });
    store.bumpReplies('t1');
    expect(store.snapshot()('t1').replies).toBe(2);
    store.bumpReplies('t1', -5);
    expect(store.snapshot()('t1').replies).toBe(0);
  });
});
