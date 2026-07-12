import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { render, screen, fireEvent } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { TweetCardComponent } from './tweet-card.component';
import { TweetDto } from '../../../core/api/models/tweet.model';
import { InteractionService } from '../../../core/api/services/interaction.service';
import { InteractionStore } from '../../../core/state/interaction.store';
import { SessionStore } from '../../../core/state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

const TWEET: TweetDto = {
  id: 't1',
  user_id: 'author',
  content: 'Hello #world and @ada — a meaningful post',
  created_at: new Date().toISOString(),
  likes_count: 5,
  retweets_count: 2,
  replies_count: 1,
  media_ids: [],
  user: { id: 'author', user_name: 'author' }
};

async function renderCard(interactions: Partial<InteractionService>) {
  const result = await render(TweetCardComponent, {
    componentInputs: { tweet: TWEET },
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: InteractionService, useValue: interactions }
    ]
  });
  const session = TestBed.inject(SessionStore);
  session.setToken(makeToken('me'));
  return result;
}

describe('TweetCardComponent', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders the author, handle and linkified content', async () => {
    await renderCard({});
    expect(screen.getAllByText('author').length).toBeGreaterThan(0);
    // hashtag + mention are wrapped in brand spans
    const article = document.querySelector('article')!;
    expect(article.innerHTML).toContain('#world');
    expect(article.innerHTML).toContain('@ada');
  });

  it('hydrates counts from the DTO into the interaction store', async () => {
    await renderCard({});
    const store = TestBed.inject(InteractionStore);
    expect(store.snapshot()('t1').likes).toBe(5);
    expect(store.snapshot()('t1').retweets).toBe(2);
  });

  it('does not re-seed counts when author-backfill swaps the tweet object (keeps optimistic like)', async () => {
    const likeTweet = vi.fn().mockReturnValue(of({ id: 'l1' }));
    const { fixture } = await renderCard({ likeTweet, unlikeTweet: vi.fn().mockReturnValue(of(undefined)) });
    const store = TestBed.inject(InteractionStore);

    // Optimistic like: count goes 5 -> 6.
    fireEvent.click(screen.getByRole('button', { name: 'Like' }));
    expect(store.snapshot()('t1').likes).toBe(6);

    // Author-backfill replaces the tweet object (same id, now carrying a user summary) with the
    // ORIGINAL stale DTO count of 5 — the effect re-fires but must not reseed.
    fixture.componentRef.setInput('tweet', { ...TWEET, user: { id: 'author', user_name: 'backfilled' } });
    fixture.detectChanges();

    expect(store.snapshot()('t1').likes).toBe(6); // optimistic +1 preserved, not reverted to 5
    expect(store.snapshot()('t1').liked).toBe(true);
  });

  it('like: optimistic flip + likeTweet call', async () => {
    const likeTweet = vi.fn().mockReturnValue(of({ id: 'l1' }));
    await renderCard({ likeTweet, unlikeTweet: vi.fn().mockReturnValue(of(undefined)) });
    fireEvent.click(screen.getByRole('button', { name: 'Like' }));
    const store = TestBed.inject(InteractionStore);
    expect(store.snapshot()('t1').liked).toBe(true);
    expect(store.snapshot()('t1').likes).toBe(6);
    expect(likeTweet).toHaveBeenCalledWith('me', 't1');
  });

  it('like rolls back on API error', async () => {
    const likeTweet = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    await renderCard({ likeTweet });
    fireEvent.click(screen.getByRole('button', { name: 'Like' }));
    const store = TestBed.inject(InteractionStore);
    expect(store.snapshot()('t1').liked).toBe(false);
    expect(store.snapshot()('t1').likes).toBe(5);
  });

  it('retweet ON: creates a retweet and remembers its id', async () => {
    const createRetweet = vi.fn().mockReturnValue(of({ id: 'rt-99' }));
    await renderCard({ createRetweet });
    fireEvent.click(screen.getByRole('button', { name: 'Retweet' }));
    const store = TestBed.inject(InteractionStore);
    expect(createRetweet).toHaveBeenCalledWith('me', { original_tweet_id: 't1', retweeter_id: 'me' });
    expect(store.snapshot()('t1').retweeted).toBe(true);
    expect(store.snapshot()('t1').retweets).toBe(3);
    expect(store.snapshot()('t1').retweetId).toBe('rt-99');
  });

  it('retweet OFF: DELETEs the tracked retweet (the M5 fix — no second create)', async () => {
    const createRetweet = vi.fn().mockReturnValue(of({ id: 'rt-99' }));
    const deleteRetweet = vi.fn().mockReturnValue(of(undefined));
    await renderCard({ createRetweet, deleteRetweet });

    const btn = screen.getByRole('button', { name: 'Retweet' });
    fireEvent.click(btn); // ON -> create, id remembered
    fireEvent.click(btn); // OFF -> should DELETE, NOT create again

    expect(createRetweet).toHaveBeenCalledTimes(1);
    expect(deleteRetweet).toHaveBeenCalledWith('me', 'rt-99');
    const store = TestBed.inject(InteractionStore);
    expect(store.snapshot()('t1').retweeted).toBe(false);
    expect(store.snapshot()('t1').retweets).toBe(2);
    expect(store.snapshot()('t1').retweetId).toBeNull();
  });

  it('retweet create error rolls back the optimistic flip', async () => {
    const createRetweet = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    await renderCard({ createRetweet });
    fireEvent.click(screen.getByRole('button', { name: 'Retweet' }));
    const store = TestBed.inject(InteractionStore);
    expect(store.snapshot()('t1').retweeted).toBe(false);
    expect(store.snapshot()('t1').retweets).toBe(2);
  });

  it('retweet ON stores null id when the create response omits an id', async () => {
    const createRetweet = vi.fn().mockReturnValue(of({}));
    await renderCard({ createRetweet });
    fireEvent.click(screen.getByRole('button', { name: 'Retweet' }));
    const store = TestBed.inject(InteractionStore);
    expect(store.snapshot()('t1').retweeted).toBe(true);
    expect(store.snapshot()('t1').retweetId).toBeNull();
  });

  it('retweet OFF without a known id keeps the flip but issues no DELETE', async () => {
    // Pre-seed the store as retweeted but with no tracked id (e.g. server-seeded repost).
    const deleteRetweet = vi.fn();
    const createRetweet = vi.fn();
    await renderCard({ createRetweet, deleteRetweet });
    const store = TestBed.inject(InteractionStore);
    store.setRetweeted('t1', true); // retweeted, retweetId stays null
    fireEvent.click(screen.getByRole('button', { name: 'Retweet' })); // toggles OFF
    expect(deleteRetweet).not.toHaveBeenCalled();
    expect(createRetweet).not.toHaveBeenCalled();
    expect(store.snapshot()('t1').retweeted).toBe(false);
  });

  it('retweet OFF delete error rolls back to retweeted (restoring the id)', async () => {
    const createRetweet = vi.fn().mockReturnValue(of({ id: 'rt-1' }));
    const deleteRetweet = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    await renderCard({ createRetweet, deleteRetweet });
    const btn = screen.getByRole('button', { name: 'Retweet' });
    fireEvent.click(btn); // ON -> id rt-1
    fireEvent.click(btn); // OFF -> delete fails -> rollback
    const store = TestBed.inject(InteractionStore);
    expect(deleteRetweet).toHaveBeenCalledWith('me', 'rt-1');
    expect(store.snapshot()('t1').retweeted).toBe(true);
    expect(store.snapshot()('t1').retweetId).toBe('rt-1');
  });

  it('does nothing when there is no authenticated user', async () => {
    const likeTweet = vi.fn();
    const result = await render(TweetCardComponent, {
      componentInputs: { tweet: TWEET },
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InteractionService, useValue: { likeTweet } }
      ]
    });
    void result;
    fireEvent.click(screen.getByRole('button', { name: 'Like' }));
    expect(likeTweet).not.toHaveBeenCalled();
  });
});
