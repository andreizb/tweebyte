import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Subject, throwError } from 'rxjs';
import { ProfilePage } from './profile.page';
import { UserDto } from '../../core/api/models/user.model';
import { TweetDto } from '../../core/api/models/tweet.model';
import { UserService } from '../../core/api/services/user.service';
import { TweetService } from '../../core/api/services/tweet.service';
import { FollowService } from '../../core/api/services/follow.service';
import { InteractionService } from '../../core/api/services/interaction.service';
import { SessionStore } from '../../core/state/session.store';
import { ViewerStateService } from '../../core/state/viewer-state.service';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

const USER: UserDto = {
  id: 'u-other',
  user_name: 'grace',
  biography: 'compiler pioneer',
  is_private: false,
  created_at: '2026-01-01T00:00:00',
  following: 10,
  followers: 9000
};

function tweet(id: string): TweetDto {
  return { id, user_id: 'u-other', content: `post ${id}`, created_at: new Date().toISOString(), media_ids: [], user: { id: 'u-other', user_name: 'grace' } };
}

/** A profile card as the real GET /tweets/user/{id} returns it: NO embedded `user`. */
function authorlessTweet(id: string, userId = 'u-other'): TweetDto {
  return { id, user_id: userId, content: `post ${id}`, created_at: new Date().toISOString(), media_ids: [] };
}

interface Opts {
  id?: string;
  me?: string;
  user?: ReturnType<typeof vi.fn>;
  byUser?: ReturnType<typeof vi.fn>;
  profile?: ReturnType<typeof vi.fn>;
  summaries?: ReturnType<typeof vi.fn>;
  /** Viewer's followed-id set (the ids they follow); defaults to none. */
  followedIds?: ReturnType<typeof vi.fn>;
  hydrateInteractions?: ReturnType<typeof vi.fn>;
}

async function renderProfile(opts: Opts = {}) {
  const userSvc = {
    get: opts.user ?? vi.fn().mockReturnValue(of(USER)),
    summaries: opts.summaries ?? vi.fn().mockReturnValue(of([]))
  };
  const tweetSvc = { byUser: opts.byUser ?? vi.fn().mockReturnValue(of([tweet('t1')])) };
  const followSvc = {
    profileInteractions:
      opts.profile ?? vi.fn().mockReturnValue(of({ follow_counts: { followers: 12345, following: 67 }, tweet_interactions: [] })),
    follow: vi.fn().mockReturnValue(of(undefined)),
    unfollow: vi.fn().mockReturnValue(of(undefined))
  };
  // The embedded tweet-cards still drive like/retweet actions through InteractionService.
  const interSvc = {
    likeTweet: vi.fn().mockReturnValue(of({})),
    unlikeTweet: vi.fn().mockReturnValue(of(undefined))
  };
  // ViewerStateService is mocked at the component boundary: its own behaviour (which
  // endpoints, the route-name drift, best-effort) is covered by viewer-state.service.spec.
  const viewerState = {
    followedIds:
      opts.followedIds ?? vi.fn().mockReturnValue(of(new Set<string>() as ReadonlySet<string>)),
    hydrateInteractions: opts.hydrateInteractions ?? vi.fn()
  };

  const result = await render(ProfilePage, {
    componentInputs: { id: opts.id ?? 'u-other' },
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: UserService, useValue: userSvc },
      { provide: TweetService, useValue: tweetSvc },
      { provide: FollowService, useValue: followSvc },
      { provide: InteractionService, useValue: interSvc },
      { provide: ViewerStateService, useValue: viewerState }
    ],
    configureTestBed: (tb) => tb.inject(SessionStore).setToken(makeToken(opts.me ?? 'me'))
  });
  return { ...result, userSvc, tweetSvc, followSvc, interSvc, viewerState };
}

describe('ProfilePage', () => {
  beforeEach(() => sessionStorage.clear());

  it('loads the user header (name + bio)', async () => {
    const { fixture } = await renderProfile();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getAllByText('grace').length).toBeGreaterThan(0);
    expect(screen.getByText('compiler pioneer')).toBeTruthy();
  });

  it('renders the profile tweets', async () => {
    const { fixture } = await renderProfile();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('profile-tweets')).toBeTruthy();
    expect(screen.getByText('post t1')).toBeTruthy();
  });

  it('backfills missing tweet authors via ONE batched summaries call', async () => {
    // The real /tweets/user/{id} returns cards without an embedded user; without backfill
    // every card would read @unknown.
    const byUser = vi.fn().mockReturnValue(of([authorlessTweet('t1'), authorlessTweet('t2')]));
    const summaries = vi.fn().mockReturnValue(of([{ id: 'u-other', user_name: 'grace' }]));
    const { fixture, userSvc } = await renderProfile({ byUser, summaries });
    await fixture.whenStable();
    fixture.detectChanges();

    // ONE request, with the single distinct author id.
    expect(userSvc.summaries).toHaveBeenCalledTimes(1);
    expect(userSvc.summaries).toHaveBeenCalledWith(['u-other']);
    // The author name now renders on the cards (and is absent from the header `?`).
    expect(screen.getAllByText('grace').length).toBeGreaterThan(0);
    expect(screen.queryByText('Unknown')).toBeNull();
  });

  it('does not call summaries when the tweets already carry their author', async () => {
    const { fixture, userSvc } = await renderProfile();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(userSvc.summaries).not.toHaveBeenCalled();
  });

  it('hydrates follow counts from the profile one-shot', async () => {
    const profile = vi.fn().mockReturnValue(of({ follow_counts: { followers: 12300, following: 67 }, tweet_interactions: [] }));
    const { fixture, followSvc } = await renderProfile({ profile });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(followSvc.profileInteractions).toHaveBeenCalledWith('u-other', ['t1']);
    expect(screen.getByText('12.3K')).toBeTruthy(); // followers from the one-shot, compacted
  });

  it("shows the FollowButton on another user's profile", async () => {
    const { fixture } = await renderProfile({ id: 'u-other', me: 'me' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByRole('button', { name: 'Follow' })).toBeTruthy();
  });

  it("marks the profile as followed when it is in the viewer's followed-id set", async () => {
    // The viewer's followed-id set contains this profile, so `followsTarget` flips true and
    // the header follow-button is seeded `[initial]="'following'"`. (The button's own
    // initial-vs-input timing is covered in follow-button.component.spec.)
    const followedIds = vi
      .fn()
      .mockReturnValue(of(new Set(['u-other', 'someone-else']) as ReadonlySet<string>));
    const { fixture, viewerState } = await renderProfile({ id: 'u-other', me: 'me', followedIds });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(viewerState.followedIds).toHaveBeenCalledWith('me');
    expect(fixture.componentInstance.followsTarget()).toBe(true);
  });

  it('leaves the profile unfollowed when it is not in the viewer\'s followed-id set', async () => {
    const followedIds = vi
      .fn()
      .mockReturnValue(of(new Set(['someone-else']) as ReadonlySet<string>));
    const { fixture } = await renderProfile({ id: 'u-other', me: 'me', followedIds });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.followsTarget()).toBe(false);
    expect(screen.getByRole('button', { name: 'Follow' })).toBeTruthy();
  });

  it('does not read the followed-id set on the viewer\'s own profile', async () => {
    const { fixture, viewerState } = await renderProfile({
      id: 'me',
      me: 'me',
      user: vi.fn().mockReturnValue(of({ ...USER, id: 'me', user_name: 'me' }))
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(viewerState.followedIds).not.toHaveBeenCalled();
  });

  it('hands the visible tweet ids and viewer id to the interaction hydrator', async () => {
    const byUser = vi.fn().mockReturnValue(of([tweet('t1'), tweet('t2')]));
    const { fixture, viewerState } = await renderProfile({ byUser, me: 'me' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(viewerState.hydrateInteractions).toHaveBeenCalledWith('me', ['t1', 't2']);
  });

  it('shows Edit profile (not Follow) on your own profile', async () => {
    const { fixture } = await renderProfile({
      id: 'me',
      me: 'me',
      user: vi.fn().mockReturnValue(of({ ...USER, id: 'me', user_name: 'me' }))
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByRole('button', { name: 'Edit profile' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Follow' })).toBeNull();
  });

  it('shows the empty state when the user has no tweets', async () => {
    const { fixture } = await renderProfile({ byUser: vi.fn().mockReturnValue(of([])) });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('profile-tweets-empty')).toBeTruthy();
  });

  it('shows not-found when the user fails to load', async () => {
    const { fixture } = await renderProfile({ user: vi.fn().mockReturnValue(throwError(() => new Error('404'))) });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('profile-notfound')).toBeTruthy();
  });

  it('shows a skeleton while the user is loading', async () => {
    const gate = new Subject<UserDto>();
    const { fixture } = await renderProfile({ user: vi.fn().mockReturnValue(gate), byUser: vi.fn().mockReturnValue(new Subject()) });
    fixture.detectChanges();
    expect(screen.getByTestId('profile-skeleton')).toBeTruthy();
    gate.next(USER);
    gate.complete();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.queryByTestId('profile-skeleton')).toBeNull();
  });

  it('opens the edit-profile dialog from the Edit profile button (own profile)', async () => {
    const { fixture } = await renderProfile({
      id: 'me',
      me: 'me',
      user: vi.fn().mockReturnValue(of({ ...USER, id: 'me', user_name: 'me' }))
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.queryByTestId('edit-profile-dialog')).toBeNull();

    screen.getByRole('button', { name: 'Edit profile' }).click();
    fixture.detectChanges();
    expect(screen.getByTestId('edit-profile-dialog')).toBeTruthy();
  });

  it('onSaved merges the updated user and closes the dialog', async () => {
    const { fixture } = await renderProfile({
      id: 'me',
      me: 'me',
      user: vi.fn().mockReturnValue(of({ ...USER, id: 'me', user_name: 'me' }))
    });
    await fixture.whenStable();
    fixture.detectChanges();
    fixture.componentInstance.editing.set(true);
    fixture.componentInstance.onSaved({ id: 'me', user_name: 'me', biography: 'fresh bio' });
    fixture.detectChanges();

    expect(fixture.componentInstance.editing()).toBe(false);
    expect(fixture.componentInstance.user()?.biography).toBe('fresh bio');
    expect(screen.queryByTestId('edit-profile-dialog')).toBeNull();
  });
});
