import { describe, expect, it, beforeEach, vi, type Mock } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Observable, of, Subject, throwError } from 'rxjs';
import { TweetDetailPage } from './tweet-detail.page';
import { TweetDto } from '../../core/api/models/tweet.model';
import { ReplyDto } from '../../core/api/models/interaction.model';
import { TweetService } from '../../core/api/services/tweet.service';
import { InteractionService } from '../../core/api/services/interaction.service';
import { SessionStore } from '../../core/state/session.store';

function makeToken(userId: string, username?: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims: Record<string, unknown> = { user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 };
  if (username) {
    claims['preferred_username'] = username;
  }
  return `${enc({ alg: 'HS256' })}.${enc(claims)}.s`;
}

const TWEET: TweetDto = {
  id: 't1',
  user_id: 'a',
  content: 'the focal post content here',
  created_at: new Date().toISOString(),
  likes_count: 3,
  retweets_count: 1,
  replies_count: 2,
  media_ids: [],
  user: { id: 'a', user_name: 'ada' }
};

function reply(id: string, content: string): ReplyDto {
  return { id, user_id: 'b', user_name: 'bob', content, created_at: new Date().toISOString(), likes_count: 0, likes: [], media_ids: [] };
}

interface Opts {
  byId?: Mock<[string], Observable<TweetDto>>;
  replies?: Mock<[string], Observable<ReplyDto[]>>;
  createReply?: ReturnType<typeof vi.fn>;
  id?: string;
  token?: string;
}

async function renderDetail(opts: Opts = {}) {
  const tweetSvc = {
    byId: opts.byId ?? vi.fn().mockReturnValue(of(TWEET))
  };
  const interSvc = {
    repliesForTweet: opts.replies ?? vi.fn().mockReturnValue(of([reply('r1', 'first reply')])),
    createReply: opts.createReply ?? vi.fn().mockReturnValue(of(reply('r-new', 'my new reply'))),
    likeTweet: vi.fn().mockReturnValue(of({})),
    unlikeTweet: vi.fn().mockReturnValue(of(undefined))
  };
  const result = await render(TweetDetailPage, {
    componentInputs: { id: opts.id ?? 't1' },
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: TweetService, useValue: tweetSvc },
      { provide: InteractionService, useValue: interSvc }
    ],
    configureTestBed: (tb) => tb.inject(SessionStore).setToken(opts.token ?? makeToken('me'))
  });
  return { ...result, tweetSvc, interSvc };
}

describe('TweetDetailPage', () => {
  beforeEach(() => sessionStorage.clear());

  it('loads the focal tweet and renders it', async () => {
    const { fixture } = await renderDetail();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByText('the focal post content here')).toBeTruthy();
  });

  it('renders the reply thread', async () => {
    const { fixture } = await renderDetail();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('replies-list')).toBeTruthy();
    expect(screen.getByText('first reply')).toBeTruthy();
  });

  it('shows the empty state when there are no replies', async () => {
    const { fixture } = await renderDetail({ replies: vi.fn().mockReturnValue(of([])) });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('replies-empty')).toBeTruthy();
  });

  it('shows not-found when the tweet fails to load', async () => {
    const { fixture } = await renderDetail({ byId: vi.fn().mockReturnValue(throwError(() => new Error('404'))) });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('detail-notfound')).toBeTruthy();
  });

  it('shows a skeleton while the tweet is loading', async () => {
    const gate = new Subject<TweetDto>();
    const { fixture } = await renderDetail({ byId: vi.fn().mockReturnValue(gate) });
    fixture.detectChanges();
    expect(screen.getByTestId('detail-skeleton')).toBeTruthy();
    gate.next(TWEET);
    gate.complete();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.queryByTestId('detail-skeleton')).toBeNull();
  });

  it('posts a reply optimistically from local state when the server echoes {id} only', async () => {
    // POST /replies returns {id} ONLY (ReplyMapper ignoreByDefault) — the row must be
    // built from session + submitted content, not the blank server echo.
    const createReply = vi.fn().mockReturnValue(of({ id: 'r-new' } as ReplyDto));
    const { fixture } = await renderDetail({ createReply, token: makeToken('me', 'ada_lovelace') });
    await fixture.whenStable();
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    cmp.replyText = 'my new reply';
    cmp.onReplyInput();
    fixture.detectChanges();
    cmp.postReply();
    fixture.detectChanges();

    expect(createReply).toHaveBeenCalledWith('me', { tweet_id: 't1', user_id: 'me', content: 'my new reply' });
    expect(cmp.replyText).toBe('');
    // content + author render from local state despite the id-only echo.
    expect(screen.getByText('my new reply')).toBeTruthy();
    expect(screen.getByText('@ada_lovelace')).toBeTruthy();
  });

  it('does not post an empty reply', async () => {
    const createReply = vi.fn();
    const { fixture } = await renderDetail({ createReply });
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.postReply();
    expect(createReply).not.toHaveBeenCalled();
  });

  it('reply error keeps the text for retry', async () => {
    const createReply = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    const { fixture } = await renderDetail({ createReply });
    await fixture.whenStable();
    fixture.detectChanges();
    const cmp = fixture.componentInstance;
    cmp.replyText = 'will fail to post';
    cmp.onReplyInput();
    cmp.postReply();
    fixture.detectChanges();
    expect(cmp.posting()).toBe(false);
  });

  it('re-loads when the route id changes (tweet → tweet)', async () => {
    const TWEET2: TweetDto = { ...TWEET, id: 't2', content: 'the second focal post', user: { id: 'c', user_name: 'cleo' } };
    const byId = vi.fn((tid: string) => of(tid === 't2' ? TWEET2 : TWEET));
    const replies = vi.fn((tid: string) =>
      of(tid === 't2' ? [reply('r2', 'second-thread reply')] : [reply('r1', 'first reply')])
    );
    const { fixture } = await renderDetail({ byId, replies });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByText('the focal post content here')).toBeTruthy();
    expect(screen.getByText('first reply')).toBeTruthy();

    fixture.componentRef.setInput('id', 't2');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byId).toHaveBeenCalledWith('t2');
    expect(screen.getByText('the second focal post')).toBeTruthy();
    expect(screen.getByText('second-thread reply')).toBeTruthy();
    // stale content from the previous tweet is gone.
    expect(screen.queryByText('the focal post content here')).toBeNull();
    expect(screen.queryByText('first reply')).toBeNull();
  });

  it('ignores a slow in-flight load superseded by a newer id', async () => {
    const slow = new Subject<TweetDto>();
    const TWEET2: TweetDto = { ...TWEET, id: 't2', content: 'the second focal post' };
    const byId = vi.fn((tid: string) => (tid === 't1' ? slow : of(TWEET2)));
    const { fixture } = await renderDetail({ byId });
    fixture.detectChanges();

    // navigate before t1 resolves
    fixture.componentRef.setInput('id', 't2');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByText('the second focal post')).toBeTruthy();

    // the late t1 response must not clobber the current t2 view
    slow.next(TWEET);
    slow.complete();
    fixture.detectChanges();
    expect(screen.getByText('the second focal post')).toBeTruthy();
    expect(screen.queryByText('the focal post content here')).toBeNull();
  });
});
