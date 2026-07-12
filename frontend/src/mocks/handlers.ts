import { delay, http, HttpResponse } from 'msw';

import { FollowDto } from '../app/core/api/models/follow.model';
import {
  LikeDto,
  ReplyDto,
  RetweetDto,
  TweetInteractionsEntryDto,
  UuidCountMap,
  UuidReplyMap
} from '../app/core/api/models/interaction.model';
import { TweetDto, TweetSummaryDto } from '../app/core/api/models/tweet.model';
import { UserDto, UserUpdateRequest } from '../app/core/api/models/user.model';
import { isEmpty, isNull, maybeFail } from './scenario';
import {
  COUNTLESS_TWEET,
  COUNTLESS_TWEET_ID,
  feedTweets,
  ME_ID,
  SPARSE_TWEET,
  SPARSE_TWEET_ID,
  TRENDS,
  TWEETS,
  USERS,
  summaryOf,
  userById
} from './seed';

/**
 * Build a structurally-valid, unsigned JWT carrying the owner user_id claim. The optional
 * `variant` (test-only) shapes the claims to exercise sessionFromToken's fallback arms.
 */
function mockJwt(userId: string, username: string, variant?: string | null): string {
  const enc = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  if (variant === 'bad') {
    // Not three dot-separated base64 segments → jwtDecode throws → sessionFromToken catch arm.
    return 'this-is-not-a-jwt';
  }
  const header = enc({ alg: 'HS256', typ: 'JWT' });
  const now = Math.floor(Date.now() / 1000);
  const base: Record<string, unknown> = { iat: now };
  if (variant !== 'no-name') {
    base['preferred_username'] = username;
  }
  if (variant !== 'noexp') {
    base['exp'] = now + 86_400;
  }
  if (variant === 'sub-only') {
    base['sub'] = userId; // no user_id → falls back to sub
  } else if (variant === 'nouser') {
    /* neither user_id nor sub → sessionFromToken returns null */
  } else {
    base['user_id'] = userId;
    base['sub'] = userId;
  }
  return `${header}.${enc(base)}.mock-signature-not-verified-client-side`;
}

const ANY = '*';
const MEDIA_BYTES = new TextEncoder().encode('Tweebyte mock media bytes\n');

const repliesByTweet = new Map<string, ReplyDto[]>(
  TWEETS.map((tweet, index) => [
    tweet.id!,
    [
      {
        id: `50000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
        user_id: USERS[(index + 1) % USERS.length].id,
        user_name: USERS[(index + 1) % USERS.length].user_name,
        content: 'This is the kind of contract-level detail that keeps the UI simple.',
        created_at: new Date(Date.now() - (index + 1) * 120_000).toISOString().replace('Z', ''),
        likes_count: index + 2,
        likes: [],
        media_ids: []
      }
    ]
  ])
);

// Ids the viewer (ME_ID / ada) already follows: grace (…003) and dijkstra (…005). Kept off
// linus (…002) and margaret (…004) so the profile follow + follow-request flows see them as
// not-yet-followed. grace also appears in who-to-follow, exercising its "Following" branch.
const FOLLOWED_BY_ME: string[] = [USERS[2].id, USERS[4].id];

const retweets: RetweetDto[] = [];
const followRequests: FollowDto[] = [
  {
    id: '60000000-0000-4000-8000-000000000001',
    user_name: USERS[3].user_name,
    follower_id: USERS[3].id,
    followed_id: ME_ID,
    created_at: new Date(Date.now() - 900_000).toISOString().replace('Z', ''),
    status: 'PENDING'
  }
];

export const handlers = [
  // Auth, user-service
  http.post(`${ANY}/user-service/auth/login`, async ({ request }) => {
    const forced = await maybeFail('login');
    if (forced) {
      return forced;
    }
    const body = (await request.json().catch(() => ({}))) as { email?: string };
    const user = USERS.find((u) => u.email === body.email) ?? USERS[0];
    await delay(120);
    // Test-only token-shape variants (?__token=…) so the e2e can drive sessionFromToken's
    // claim-fallback arms: a sub-only token (no user_id), an exp-less token, a token with no
    // display name, no owner claim, or a structurally-broken token.
    const variant =
      typeof location !== 'undefined'
        ? new URLSearchParams(location.search).get('__token')
        : null;
    return HttpResponse.json({ token: mockJwt(user.id, user.user_name, variant) });
  }),

  http.post(`${ANY}/user-service/auth/register`, async () => {
    const forced = await maybeFail('register');
    if (forced) {
      return forced;
    }
    const me = userById(ME_ID)!;
    const variant =
      typeof location !== 'undefined'
        ? new URLSearchParams(location.search).get('__token')
        : null;
    await delay(160);
    return HttpResponse.json({ token: mockJwt(me.id, me.user_name, variant) });
  }),

  // Users, user-service
  http.get(`${ANY}/user-service/users/summary/name/:name`, ({ params }) => {
    const name = String(params['name']).toLowerCase();
    const u = USERS.find((user) => user.user_name.toLowerCase() === name);
    return u ? HttpResponse.json(summaryOf(u)) : notFound();
  }),

  http.get(`${ANY}/user-service/users/summary/:id`, ({ params }) => {
    const u = userById(String(params['id']));
    return u ? HttpResponse.json(summaryOf(u)) : notFound();
  }),

  http.post(`${ANY}/user-service/users/summaries`, async ({ request }) => {
    const forced = await maybeFail('summaries');
    if (forced) {
      return forced;
    }
    const ids = (await request.json().catch(() => [])) as string[];
    if (isEmpty('summaries')) {
      return HttpResponse.json([]);
    }
    if (isNull('summaries')) {
      return HttpResponse.json(
        ids
          .slice(0, 1)
          .map((id) => userById(id))
          .filter(Boolean)
          .map((u) => summaryOf(u!))
      );
    }
    return HttpResponse.json(ids.map((id) => userById(id)).filter(Boolean).map((u) => summaryOf(u!)));
  }),

  http.get(`${ANY}/user-service/users/search/:term`, async ({ params, request }) => {
    const forced = await maybeFail('search-users');
    if (forced) {
      return forced;
    }
    const term = String(params['term']).toLowerCase();
    const matches = isEmpty('search-users')
      ? []
      : USERS.filter((u) => u.user_name.toLowerCase().includes(term)).map(summaryOf);
    return HttpResponse.json(pageFromRequest(matches, request, 20));
  }),

  http.get(`${ANY}/user-service/users/:id`, async ({ params }) => {
    const forced = await maybeFail('profile');
    if (forced) {
      return forced;
    }
    const u = userById(String(params['id']));
    if (!u) {
      return notFound();
    }
    return HttpResponse.json({ ...u, tweets: TWEETS.filter((t) => t.user_id === u.id) });
  }),

  http.put(`${ANY}/user-service/users/:id`, async ({ params, request }) => {
    const forced = await maybeFail('profile-update');
    if (forced) {
      return forced;
    }
    const u = userById(String(params['id']));
    if (!u) {
      return notFound();
    }
    const form = await request.formData().catch(() => new FormData());
    const patch: UserUpdateRequest = {};
    for (const [key, value] of form.entries()) {
      if (typeof value === 'string') {
        (patch as Record<string, string>)[key] = value;
      }
    }
    Object.assign(u, userPatchToDto(patch));
    return HttpResponse.json(u satisfies UserDto);
  }),

  http.delete(`${ANY}/user-service/users/:id`, ({ params }) => {
    const idx = USERS.findIndex((u) => u.id === String(params['id']));
    if (idx !== -1) {
      USERS.splice(idx, 1);
    }
    return noContent();
  }),

  // Media, user-service
  http.post(`${ANY}/user-service/media`, async () => {
    const forced = await maybeFail('media');
    if (forced) {
      return forced;
    }
    await delay(80);
    return HttpResponse.json({ id: nextId('70000000') }, { status: 201 });
  }),

  http.post(`${ANY}/user-service/media/:id/preview`, async () => {
    await delay(120);
    return HttpResponse.json({ id: nextId('71000000') });
  }),

  http.post(`${ANY}/user-service/media/:id/reveal`, () => bytesResponse()),

  http.get(`${ANY}/user-service/media/:id/exists`, () => HttpResponse.json({ exists: true })),

  http.get(`${ANY}/user-service/media/:id`, async () => {
    // Honour a forced failure so the bearer blob fetch (MediaService.fetchBlobUrl) sees a
    // non-ok response and the Avatar / MediaThumb tiles take their error arm.
    const forced = await maybeFail('media');
    return forced ?? bytesResponse();
  }),

  http.delete(`${ANY}/user-service/media/cache`, () => noContent()),

  // Tweets, tweet-service
  http.get(`${ANY}/tweet-service/tweets/:id/feed`, async ({ request }) => {
    const forced = await maybeFail('feed');
    if (forced) {
      return forced;
    }
    await delay(100);
    if (isNull('feed')) {
      return HttpResponse.json(null);
    }
    const rows = isEmpty('feed') ? [] : feedTweets();
    return HttpResponse.json(pageFromRequest(rows, request, 20));
  }),

  http.get(`${ANY}/tweet-service/tweets/search/hashtag/:term`, ({ params }) => {
    const term = String(params['term']).replace(/^#/, '').toLowerCase();
    return HttpResponse.json(
      TWEETS.filter((t) => (t.hashtags ?? []).some((h) => h.text?.toLowerCase() === term))
    );
  }),

  http.get(`${ANY}/tweet-service/tweets/search/:term`, async ({ params }) => {
    const forced = await maybeFail('search-tweets');
    if (forced) {
      return forced;
    }
    const term = String(params['term']).toLowerCase();
    const rows = isEmpty('search-tweets')
      ? []
      : TWEETS.filter((t) => t.content?.toLowerCase().includes(term));
    return HttpResponse.json(rows);
  }),

  http.get(`${ANY}/tweet-service/tweets/hashtag/popular`, async () => {
    const forced = await maybeFail('trends');
    if (forced) {
      return forced;
    }
    return HttpResponse.json(TRENDS.map((t) => ({ id: t.id, text: t.text, count: t.count })));
  }),

  http.get(`${ANY}/tweet-service/tweets/user/:id/summary`, ({ params }) => {
    const rows: TweetSummaryDto[] = TWEETS.filter((t) => t.user_id === String(params['id'])).map(
      tweetSummary
    );
    return HttpResponse.json(rows);
  }),

  http.get(`${ANY}/tweet-service/tweets/user/:id`, async ({ params, request }) => {
    const forced = await maybeFail('profile-tweets');
    if (forced) {
      return forced;
    }
    // `empty('profile')` forces a no-tweets profile so the page's empty-state arms render.
    if (isNull('profile-tweets')) {
      return HttpResponse.json(null);
    }
    const mine = isEmpty('profile')
      ? []
      : TWEETS.filter((t) => t.user_id === String(params['id'])).map(withoutEmbeddedUser);
    return HttpResponse.json(pageFromRequest(mine, request, 20));
  }),

  http.get(`${ANY}/tweet-service/tweets/:tweetId/summary`, ({ params }) => {
    const t = TWEETS.find((x) => x.id === String(params['tweetId']));
    return t ? HttpResponse.json(tweetSummary(t)) : notFound();
  }),

  http.get(`${ANY}/tweet-service/tweets/:tweetId`, ({ params }) => {
    const id = String(params['tweetId']);
    // The maximally-sparse tweet is served only here (the detail route), not in the feed,
    // to drive the TweetCard absent-field fallback arms without an unnamed-link feed row.
    if (id === SPARSE_TWEET_ID) {
      return HttpResponse.json(SPARSE_TWEET);
    }
    if (id === COUNTLESS_TWEET_ID) {
      return HttpResponse.json(COUNTLESS_TWEET);
    }
    const t = TWEETS.find((x) => x.id === id);
    return t ? HttpResponse.json(t) : notFound();
  }),

  http.post(`${ANY}/tweet-service/tweets/:id`, async ({ request, params }) => {
    const forced = await maybeFail('compose');
    if (forced) {
      return forced;
    }
    const body = (await request.json().catch(() => ({}))) as {
      content?: string;
      media_ids?: string[];
    };
    const author = userById(String(params['id'])) ?? userById(ME_ID)!;
    const created: TweetDto = {
      id: nextId('20000000'),
      user_id: author.id,
      content: body.content ?? '',
      created_at: nowIso(),
      media_ids: body.media_ids ?? [],
      mentions: [],
      hashtags: hashtagsFrom(body.content ?? ''),
      likes_count: 0,
      replies_count: 0,
      retweets_count: 0,
      user: summaryOf(author)
    };
    TWEETS.unshift(created);
    await delay(120);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.put(`${ANY}/tweet-service/tweets/:userId/:tweetId`, async ({ request, params }) => {
    const tweet = TWEETS.find((t) => t.id === String(params['tweetId']));
    if (!tweet) {
      return notFound();
    }
    const body = (await request.json().catch(() => ({}))) as { content?: string };
    tweet.content = body.content ?? tweet.content;
    tweet.hashtags = hashtagsFrom(tweet.content ?? '');
    return HttpResponse.json(tweet);
  }),

  http.delete(`${ANY}/tweet-service/tweets/:userId/:tweetId`, ({ params }) => {
    const idx = TWEETS.findIndex((t) => t.id === String(params['tweetId']));
    if (idx !== -1) {
      TWEETS.splice(idx, 1);
    }
    return noContent();
  }),

  // AI streaming, tweet-service
  http.get(`${ANY}/tweet-service/tweets/ai/users/:uid/conversations/:cid/mock-stream`, ({ request }) => {
    const url = new URL(request.url);
    return sseResponse(
      scriptedTokens(Number(url.searchParams.get('tokens') ?? 60)),
      Number(url.searchParams.get('itlMs') ?? 35)
    );
  }),

  http.post(`${ANY}/tweet-service/tweets/ai/users/:uid/conversations/:cid/summarize`, async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as { prompt?: string };
    return sseResponse(summaryTokens(body.prompt ?? ''), 30);
  }),

  http.post(
    `${ANY}/tweet-service/tweets/ai/users/:uid/conversations/:cid/summarize-with-tool`,
    async ({ request }) => {
      const body = (await request.json().catch(() => ({}))) as { prompt?: string };
      return sseResponse(summaryTokens(body.prompt ?? '', '[tool:trend-lookup]'), 30);
    }
  ),

  http.post(
    `${ANY}/tweet-service/tweets/ai/users/:uid/conversations/:cid/buffered`,
    async ({ request }) => {
      const body = (await request.json().catch(() => ({}))) as { prompt?: string };
      await delay(200);
      return HttpResponse.json({ response: summaryTokens(body.prompt ?? '').join('') });
    }
  ),

  // Follows, interaction-service
  http.get(`${ANY}/interaction-service/follows/:id/followers`, ({ params }) => {
    const id = String(params['id']);
    return HttpResponse.json(
      USERS.filter((u) => u.id !== id).slice(0, 3).map((u, index) => followDto(u.id, id, index))
    );
  }),

  http.get(`${ANY}/interaction-service/follows/:id/following`, () => bytesResponse()),

  http.get(`${ANY}/interaction-service/follows/:id/followers/count`, ({ params }) => {
    return HttpResponse.json(userById(String(params['id']))?.followers ?? 0);
  }),

  http.get(`${ANY}/interaction-service/follows/:id/following/count`, ({ params }) => {
    return HttpResponse.json(userById(String(params['id']))?.following ?? 0);
  }),

  http.get(`${ANY}/interaction-service/follows/:id/followers/identifiers`, async () => {
    const forced = await maybeFail('followed-ids');
    if (forced) {
      return forced;
    }
    // The viewer's followed-id set (route name drifts server-side; see ViewerStateService).
    // Seed it as grace + dijkstra so a who-to-follow suggestion already in the set renders
    // "Following", while linus (public) and margaret (private) start un-followed — the
    // initial state the profile follow / follow-request flows act on.
    return HttpResponse.json(FOLLOWED_BY_ME);
  }),

  http.get(`${ANY}/interaction-service/follows/:id/counts`, ({ params }) => {
    const u = userById(String(params['id']));
    return HttpResponse.json({ followers: u?.followers ?? 0, following: u?.following ?? 0 });
  }),

  http.post(`${ANY}/interaction-service/follows/:id/profile-interactions`, async ({ request, params }) => {
    const forced = await maybeFail('profile-interactions');
    if (forced) {
      return forced;
    }
    const ids = (await request.json().catch(() => [])) as string[];
    const u = userById(String(params['id']));
    const body: {
      follow_counts: { followers: number; following: number };
      tweet_interactions?: TweetInteractionsEntryDto[];
    } = {
      follow_counts: { followers: u?.followers ?? 0, following: u?.following ?? 0 },
      tweet_interactions: isEmpty('profile-interactions') ? undefined : interactionRows(ids)
    };
    return HttpResponse.json(body);
  }),

  http.get(`${ANY}/interaction-service/follows/:id/requests`, () => HttpResponse.json(followRequests)),

  http.post(`${ANY}/interaction-service/follows/:userId/:followedId`, async () => {
    const forced = await maybeFail('follow');
    return forced ?? noContent();
  }),

  http.put(`${ANY}/interaction-service/follows/:userId/:requestId/:status`, () => noContent()),

  http.delete(`${ANY}/interaction-service/follows/:userId/:followedId`, async () => {
    const forced = await maybeFail('follow');
    return forced ?? noContent();
  }),

  // Likes, interaction-service
  http.get(`${ANY}/interaction-service/likes/user/:userId`, async ({ params, request }) => {
    const forced = await maybeFail('viewer-likes');
    if (forced) {
      return forced;
    }
    const likes = TWEETS.slice(0, 4).map((tweet, index) => likeDto(String(params['userId']), tweet, index));
    return HttpResponse.json(pageFromRequest(likes, request, 10));
  }),

  http.get(`${ANY}/interaction-service/likes/tweet/:tweetId`, ({ params, request }) => {
    const tweet = TWEETS.find((t) => t.id === String(params['tweetId']));
    const likes = tweet
      ? USERS.slice(0, 3).map((user, index) => likeDto(user.id, tweet, index))
      : [];
    return HttpResponse.json(pageFromRequest(likes, request, 10));
  }),

  http.get(`${ANY}/interaction-service/likes/:tweetId/count`, ({ params }) => {
    const tweet = TWEETS.find((t) => t.id === String(params['tweetId']));
    return HttpResponse.json(tweet?.likes_count ?? 0);
  }),

  http.post(`${ANY}/interaction-service/likes/counts`, async ({ request }) => {
    const ids = (await request.json().catch(() => [])) as string[];
    return HttpResponse.json(countMap(ids, (tweet) => tweet.likes_count ?? 0));
  }),

  http.post(`${ANY}/interaction-service/likes/:userId/tweets/:tweetId`, async ({ params }) => {
    const forced = await maybeFail('like');
    if (forced) {
      return forced;
    }
    const tweet = TWEETS.find((t) => t.id === String(params['tweetId']));
    return tweet ? HttpResponse.json(likeDto(String(params['userId']), tweet, 0)) : notFound();
  }),

  http.delete(`${ANY}/interaction-service/likes/:userId/tweets/:tweetId`, async () => {
    const forced = await maybeFail('like');
    return forced ?? noContent();
  }),

  http.post(`${ANY}/interaction-service/likes/:userId/replies/:replyId`, ({ params }) => {
    const reply = findReply(String(params['replyId']));
    const tweet = TWEETS[0];
    return reply && tweet ? HttpResponse.json(likeDto(String(params['userId']), tweet, 1)) : notFound();
  }),

  http.delete(`${ANY}/interaction-service/likes/:userId/replies/:replyId`, () => noContent()),

  // Replies, interaction-service
  http.post(`${ANY}/interaction-service/replies/:userId`, async ({ request, params }) => {
    const forced = await maybeFail('reply');
    if (forced) {
      return forced;
    }
    const body = (await request.json().catch(() => ({}))) as {
      tweet_id?: string;
      content?: string;
      media_ids?: string[];
    };
    const user = userById(String(params['userId'])) ?? userById(ME_ID)!;
    const reply: ReplyDto = {
      id: nextId('51000000'),
      user_id: user.id,
      user_name: user.user_name,
      content: body.content ?? '',
      created_at: nowIso(),
      likes_count: 0,
      likes: [],
      media_ids: body.media_ids ?? []
    };
    const tweetId = body.tweet_id ?? TWEETS[0]?.id ?? '';
    repliesByTweet.set(tweetId, [reply, ...(repliesByTweet.get(tweetId) ?? [])]);
    return HttpResponse.json(reply, { status: 201 });
  }),

  http.put(`${ANY}/interaction-service/replies/:userId/:replyId`, async ({ request, params }) => {
    const reply = findReply(String(params['replyId']));
    if (!reply) {
      return notFound();
    }
    const body = (await request.json().catch(() => ({}))) as { content?: string };
    reply.content = body.content ?? reply.content;
    return noContent();
  }),

  http.delete(`${ANY}/interaction-service/replies/:userId/:replyId`, ({ params }) => {
    deleteReply(String(params['replyId']));
    return noContent();
  }),

  http.get(`${ANY}/interaction-service/replies/tweet/:tweetId`, async ({ params, request }) => {
    const forced = await maybeFail('reply');
    if (forced) {
      return forced;
    }
    if (isNull('reply')) {
      return HttpResponse.json(null);
    }
    const rows = isEmpty('reply') ? [] : repliesByTweet.get(String(params['tweetId'])) ?? [];
    return HttpResponse.json(pageFromRequest(rows, request, 10));
  }),

  http.get(`${ANY}/interaction-service/replies/tweet/:tweetId/count`, ({ params }) => {
    return HttpResponse.json(repliesByTweet.get(String(params['tweetId']))?.length ?? 0);
  }),

  http.get(`${ANY}/interaction-service/replies/tweet/:tweetId/top`, ({ params }) => {
    return HttpResponse.json((repliesByTweet.get(String(params['tweetId'])) ?? [])[0] ?? null);
  }),

  http.post(`${ANY}/interaction-service/replies/tweet/counts`, async ({ request }) => {
    const ids = (await request.json().catch(() => [])) as string[];
    const out: UuidCountMap = {};
    ids.forEach((id) => {
      out[id] = repliesByTweet.get(id)?.length ?? 0;
    });
    return HttpResponse.json(out);
  }),

  http.post(`${ANY}/interaction-service/replies/tweet/top`, async ({ request }) => {
    const ids = (await request.json().catch(() => [])) as string[];
    const out: UuidReplyMap = {};
    ids.forEach((id) => {
      out[id] = (repliesByTweet.get(id) ?? [])[0] ?? null;
    });
    return HttpResponse.json(out);
  }),

  // Retweets, interaction-service
  http.post(`${ANY}/interaction-service/retweets/:userId`, async ({ request, params }) => {
    const forced = await maybeFail('retweet');
    if (forced) {
      return forced;
    }
    const body = (await request.json().catch(() => ({}))) as {
      original_tweet_id?: string;
      content?: string;
      media_ids?: string[];
    };
    const user = userById(String(params['userId'])) ?? userById(ME_ID)!;
    const tweet = TWEETS.find((t) => t.id === body.original_tweet_id) ?? TWEETS[0];
    if (!tweet) {
      return notFound();
    }
    const retweet: RetweetDto = {
      id: nextId('52000000'),
      created_at: nowIso(),
      content: body.content,
      user: summaryOf(user),
      tweet,
      media_ids: body.media_ids ?? []
    };
    if (isNull('retweet')) {
      delete retweet.id;
    }
    retweets.unshift(retweet);
    return HttpResponse.json(retweet, { status: 201 });
  }),

  http.put(`${ANY}/interaction-service/retweets/:userId/:retweetId`, async ({ request, params }) => {
    const retweet = retweets.find((r) => r.id === String(params['retweetId']));
    if (!retweet) {
      return notFound();
    }
    const body = (await request.json().catch(() => ({}))) as { content?: string };
    retweet.content = body.content;
    return noContent();
  }),

  http.delete(`${ANY}/interaction-service/retweets/:userId/:retweetId`, async ({ params }) => {
    const forced = await maybeFail('retweet');
    if (forced) {
      return forced;
    }
    const index = retweets.findIndex((r) => r.id === String(params['retweetId']));
    if (index !== -1) {
      retweets.splice(index, 1);
    }
    return noContent();
  }),

  http.get(`${ANY}/interaction-service/retweets/user/:userId`, async ({ params, request }) => {
    const forced = await maybeFail('viewer-retweets');
    if (forced) {
      return forced;
    }
    if (isNull('viewer-retweets')) {
      const user = userById(String(params['userId'])) ?? USERS[0];
      return HttpResponse.json([
        {
          created_at: nowIso(),
          user: summaryOf(user),
          tweet: TWEETS[0],
          media_ids: []
        } satisfies RetweetDto
      ]);
    }
    return HttpResponse.json(
      pageFromRequest(retweets.filter((r) => r.user?.id === String(params['userId'])), request, 10)
    );
  }),

  http.get(`${ANY}/interaction-service/retweets/tweet/:tweetId`, ({ params, request }) => {
    return HttpResponse.json(
      pageFromRequest(retweets.filter((r) => r.tweet?.id === String(params['tweetId'])), request, 10)
    );
  }),

  http.get(`${ANY}/interaction-service/retweets/tweet/:tweetId/count`, ({ params }) => {
    const existing = TWEETS.find((t) => t.id === String(params['tweetId']))?.retweets_count ?? 0;
    return HttpResponse.json(existing + retweets.filter((r) => r.tweet?.id === String(params['tweetId'])).length);
  }),

  http.post(`${ANY}/interaction-service/retweets/tweet/counts`, async ({ request }) => {
    const ids = (await request.json().catch(() => [])) as string[];
    return HttpResponse.json(countMap(ids, (tweet) => tweet.retweets_count ?? 0));
  }),

  // Batched tweet enrichment, interaction-service
  http.post(`${ANY}/interaction-service/tweets/interactions`, async ({ request }) => {
    const ids = (await request.json().catch(() => [])) as string[];
    return HttpResponse.json(interactionRows(ids));
  }),

  // Recommendations, interaction-service
  http.get(`${ANY}/interaction-service/recommendations/:id/follow`, async ({ params }) => {
    const forced = await maybeFail('recommendations');
    if (forced) {
      return forced;
    }
    const meId = String(params['id']);
    return HttpResponse.json(USERS.filter((u) => u.id !== meId).slice(0, 3).map(summaryOf));
  }),

  http.get(`${ANY}/interaction-service/recommendations/hashtags`, () =>
    HttpResponse.json(TRENDS.map((t) => ({ id: t.id, text: t.text, count: t.count })))
  ),

  // Health
  http.get(`${ANY}/actuator/health`, async () => {
    const forced = await maybeFail('health');
    if (forced) {
      return forced;
    }
    return HttpResponse.json({ status: 'UP' });
  })
];

function noContent() {
  return new HttpResponse(null, { status: 204 });
}

function notFound() {
  return HttpResponse.json({ error: 'Not found' }, { status: 404 });
}

function bytesResponse() {
  return new HttpResponse(MEDIA_BYTES, {
    status: 206,
    headers: {
      'Content-Type': 'application/octet-stream',
      'Content-Range': `bytes 0-${MEDIA_BYTES.byteLength - 1}/${MEDIA_BYTES.byteLength}`
    }
  });
}

function pageFromRequest<T>(rows: T[], request: Request, defaultSize: number): T[] {
  const url = new URL(request.url);
  const page = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? defaultSize);
  return rows.slice(page * size, page * size + size);
}

function nowIso(): string {
  return new Date().toISOString().replace('Z', '');
}

// Start well past the seed id range (seed tweets/replies/etc. use low suffixes) so a
// runtime-created entity never collides with a seeded one — a collision would make a new
// tweet share another tweet's interaction state (counts, liked/retweeted flags).
let idCounter = 1000;
function nextId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-0000-4000-8000-${String(idCounter).padStart(12, '0')}`;
}

function userPatchToDto(body: UserUpdateRequest): Partial<UserDto> {
  return {
    user_name: body.userName,
    email: body.email,
    biography: body.biography,
    is_private: body.isPrivate,
    birth_date: body.birthDate,
    profile_picture_id: body.profilePictureId
  };
}

function tweetSummary(tweet: TweetDto): TweetSummaryDto {
  return {
    id: tweet.id,
    hashtags: (tweet.hashtags ?? []).map((h) => h.text ?? '').filter(Boolean),
    mentions: (tweet.mentions ?? []).map((m) => m.text ?? '').filter(Boolean)
  };
}

function withoutEmbeddedUser(tweet: TweetDto): TweetDto {
  const copy = { ...tweet };
  delete copy.user;
  return copy;
}

function hashtagsFrom(content: string) {
  const tags = [...content.matchAll(/#([\p{L}0-9_]+)/gu)].map((m, index) => ({
    id: nextId('30000000'),
    text: m[1],
    count: index + 1
  }));
  return tags;
}

function followDto(followerId: string, followedId: string, index: number): FollowDto {
  const user = userById(followerId)!;
  return {
    id: `60000000-0000-4000-8000-${String(index + 10).padStart(12, '0')}`,
    user_name: user.user_name,
    follower_id: followerId,
    followed_id: followedId,
    created_at: nowIso(),
    status: 'ACCEPTED'
  };
}

function likeDto(userId: string, tweet: TweetDto, index: number): LikeDto {
  return {
    id: `53000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
    user: summaryOf(userById(userId) ?? USERS[0]),
    tweet,
    created_at: nowIso()
  };
}

function findReply(replyId: string): ReplyDto | undefined {
  for (const replies of repliesByTweet.values()) {
    const found = replies.find((reply) => reply.id === replyId);
    if (found) {
      return found;
    }
  }
  return undefined;
}

function deleteReply(replyId: string): void {
  for (const [tweetId, replies] of repliesByTweet.entries()) {
    repliesByTweet.set(
      tweetId,
      replies.filter((reply) => reply.id !== replyId)
    );
  }
}

function countMap(ids: string[], pick: (tweet: TweetDto) => number): UuidCountMap {
  const out: UuidCountMap = {};
  ids.forEach((id) => {
    const tweet = TWEETS.find((t) => t.id === id);
    out[id] = tweet ? pick(tweet) : 0;
  });
  return out;
}

function interactionRows(ids: string[]): TweetInteractionsEntryDto[] {
  return ids.map((id) => {
    const tweet = TWEETS.find((t) => t.id === id);
    return {
      tweet_id: id,
      likes: tweet?.likes_count ?? 0,
      replies: repliesByTweet.get(id)?.length ?? tweet?.replies_count ?? 0,
      retweets: tweet?.retweets_count ?? 0,
      top_reply: (repliesByTweet.get(id) ?? [])[0]
    };
  });
}

function scriptedTokens(n: number): string[] {
  const base =
    'Tweebyte streams tokens over a POST body as text event stream so the frontend reads response body directly. '.split(
      ' '
    );
  return Array.from({ length: n }, (_, i) => `${base[i % base.length] ?? 'token'} `);
}

function summaryTokens(prompt: string, tool?: string): string[] {
  const text = `Here is a quick summary of your timeline. The main theme is the async versus reactive tradeoff, with identical JSON contracts and a swappable gateway base URL. ${
    prompt ? `You asked: "${prompt.slice(0, 60)}". ` : ''
  }That is the honest read.`;
  const words = text.split(' ').map((w) => `${w} `);
  if (tool) {
    words.splice(Math.min(12, words.length), 0, `${tool} `);
  }
  return words;
}

function sseResponse(tokens: string[], itlMs: number) {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      for (const token of tokens) {
        controller.enqueue(encoder.encode(`data: ${token}\n\n`));
        await delay(itlMs);
      }
      controller.close();
    }
  });
  return new HttpResponse(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache'
    }
  });
}
