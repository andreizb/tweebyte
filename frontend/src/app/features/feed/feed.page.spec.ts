import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Subject, throwError } from 'rxjs';
import { FeedPage } from './feed.page';
import { TweetDto } from '../../core/api/models/tweet.model';
import { TweetService } from '../../core/api/services/tweet.service';
import { InteractionService } from '../../core/api/services/interaction.service';
import { UserService } from '../../core/api/services/user.service';
import { SessionStore } from '../../core/state/session.store';
import { ViewerStateService } from '../../core/state/viewer-state.service';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

function tweet(id: string): TweetDto {
  return {
    id,
    user_id: 'a',
    content: `meaningful post ${id}`,
    created_at: new Date().toISOString(),
    likes_count: 1,
    retweets_count: 0,
    replies_count: 0,
    media_ids: [],
    user: { id: 'a', user_name: 'ada' }
  };
}

/** A feed card as the real GET /tweets/{uid}/feed returns it: NO embedded `user`. */
function authorlessTweet(id: string, userId: string): TweetDto {
  return {
    id,
    user_id: userId,
    content: `meaningful post ${id}`,
    created_at: new Date().toISOString(),
    likes_count: 0,
    retweets_count: 0,
    replies_count: 0,
    media_ids: []
  };
}

interface RenderOpts {
  feed?: ReturnType<typeof vi.fn>;
  enrich?: ReturnType<typeof vi.fn>;
  summaries?: ReturnType<typeof vi.fn>;
}

async function renderFeed(opts: RenderOpts = {}) {
  const feedFn = opts.feed ?? vi.fn().mockReturnValue(of([tweet('t1'), tweet('t2')]));
  const enrichFn = opts.enrich ?? vi.fn().mockReturnValue(of([]));
  const summariesFn = opts.summaries ?? vi.fn().mockReturnValue(of([]));
  const tweetSvc = { feed: feedFn, create: vi.fn().mockReturnValue(of(tweet('new'))) };
  const interSvc = {
    enrich: enrichFn,
    likeTweet: vi.fn().mockReturnValue(of({})),
    unlikeTweet: vi.fn().mockReturnValue(of(undefined))
  };
  const userSvc = { summaries: summariesFn };
  // ViewerStateService is mocked at the component boundary; its real behaviour (endpoints,
  // intersect-with-visible, best-effort) is covered by viewer-state.service.spec.
  const viewerState = { hydrateInteractions: vi.fn() };

  // Seed the session BEFORE the feed loads (FeedStore reads userId on init).
  const result = await render(FeedPage, {
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: TweetService, useValue: tweetSvc },
      { provide: InteractionService, useValue: interSvc },
      { provide: UserService, useValue: userSvc },
      { provide: ViewerStateService, useValue: viewerState }
    ],
    configureTestBed: (tb) => {
      tb.inject(SessionStore).setToken(makeToken('me'));
    }
  });
  return { ...result, feedFn, enrichFn, summariesFn, tweetSvc, interSvc, userSvc, viewerState };
}

describe('FeedPage', () => {
  beforeEach(() => sessionStorage.clear());

  it('loads the feed and renders tweet cards', async () => {
    const { fixture } = await renderFeed();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('feed-list')).toBeTruthy();
    expect(screen.getByText(/meaningful post t1/)).toBeTruthy();
    expect(screen.getByText(/meaningful post t2/)).toBeTruthy();
  });

  it('hydrates interaction counts via the batched enrich call (one call for the whole page)', async () => {
    const enrich = vi
      .fn()
      .mockReturnValue(of([{ tweet_id: 't1', likes: 42, retweets: 7, replies: 3 }]));
    const { fixture } = await renderFeed({ enrich });
    await fixture.whenStable();
    fixture.detectChanges();
    // The page batches ALL visible tweet ids into a single enrichment request.
    expect(enrich).toHaveBeenCalledTimes(1);
    expect(enrich).toHaveBeenCalledWith(['t1', 't2']);
  });

  it("hands the visible tweet ids and viewer id to the interaction hydrator (once per load)", async () => {
    // Read DTOs carry no viewer-state, so the page delegates resolving the viewer's
    // liked/retweeted cards to the viewer-state service, batched for the whole visible page.
    const { fixture, viewerState } = await renderFeed();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(viewerState.hydrateInteractions).toHaveBeenCalledTimes(1);
    expect(viewerState.hydrateInteractions).toHaveBeenCalledWith('me', ['t1', 't2']);
  });

  it('backfills missing tweet authors via ONE batched summaries call and renders the real name', async () => {
    // The real feed returns tweets WITHOUT an embedded user; cards would otherwise read @unknown.
    const feed = vi
      .fn()
      .mockReturnValue(of([authorlessTweet('t1', 'a'), authorlessTweet('t2', 'b'), authorlessTweet('t3', 'a')]));
    const summaries = vi.fn().mockReturnValue(
      of([
        { id: 'a', user_name: 'ada' },
        { id: 'b', user_name: 'grace' }
      ])
    );
    const { fixture } = await renderFeed({ feed, summaries });
    await fixture.whenStable();
    fixture.detectChanges();

    // ONE request, with the DISTINCT missing author ids (a, b) — not one per tweet.
    expect(summaries).toHaveBeenCalledTimes(1);
    expect(summaries).toHaveBeenCalledWith(['a', 'b']);
    // The merged author surfaces on the cards.
    expect(screen.getAllByText('ada').length).toBeGreaterThan(0);
    expect(screen.getByText('grace')).toBeTruthy();
    expect(screen.queryByText('Unknown')).toBeNull();
  });

  it('does not call summaries when every tweet already carries its author', async () => {
    const { fixture, summariesFn } = await renderFeed();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(summariesFn).not.toHaveBeenCalled();
  });

  it('leaves cards on their fallback when the summaries backfill fails', async () => {
    const feed = vi.fn().mockReturnValue(of([authorlessTweet('t1', 'a')]));
    const summaries = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    const { fixture } = await renderFeed({ feed, summaries });
    await fixture.whenStable();
    fixture.detectChanges();
    // Still renders the list; the author just falls back (best-effort backfill).
    expect(screen.getByTestId('feed-list')).toBeTruthy();
    expect(screen.getByText('Unknown')).toBeTruthy();
  });

  it('shows the empty state when the feed is empty', async () => {
    const { fixture } = await renderFeed({ feed: vi.fn().mockReturnValue(of([])) });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('feed-empty')).toBeTruthy();
  });

  it('shows a loading skeleton while the first page is in flight', async () => {
    const gate = new Subject<TweetDto[]>();
    const { fixture } = await renderFeed({ feed: vi.fn().mockReturnValue(gate) });
    fixture.detectChanges();
    expect(screen.getByTestId('feed-skeletons')).toBeTruthy();
    gate.next([tweet('t1')]);
    gate.complete();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.queryByTestId('feed-skeletons')).toBeNull();
  });

  it('prepends an optimistically-composed tweet to the top', async () => {
    const { fixture } = await renderFeed();
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.onPosted(tweet('fresh'));
    fixture.detectChanges();
    const items = screen.getByTestId('feed-list').querySelectorAll('li');
    expect(items[0].textContent).toContain('meaningful post fresh');
  });

  it('a feed load error renders the error state with a Try-again action', async () => {
    const { fixture } = await renderFeed({ feed: vi.fn().mockReturnValue(throwError(() => new Error('x'))) });
    await fixture.whenStable();
    fixture.detectChanges();
    // The error state (not the empty state) renders, with a retry button.
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy();
    expect(screen.queryByTestId('feed-empty')).toBeNull();
    expect(screen.queryByTestId('feed-list')).toBeNull();
  });

  it('Try again re-loads the feed after an error', async () => {
    const feed = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new Error('x')))
      .mockReturnValueOnce(of([tweet('t1')]));
    const { fixture } = await renderFeed({ feed });
    await fixture.whenStable();
    fixture.detectChanges();
    screen.getByRole('button', { name: 'Try again' }).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('feed-list')).toBeTruthy();
    expect(screen.getByText(/meaningful post t1/)).toBeTruthy();
  });
});
