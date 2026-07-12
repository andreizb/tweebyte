/**
 * Mock seed data — meaningful, X-grade content (real handles, real-sounding posts) so
 * the UI looks alive without a Spring backend. Shapes are byte-identical to the DTOs.
 *
 * This is dev/demo data only; MSW serves it. Ids are stable UUIDs so cross-references
 * (tweet.user_id -> user.id, mention.user_id, etc.) resolve.
 */
import { TweetDto } from '../app/core/api/models/tweet.model';
import { UserDto, UserSummaryDto } from '../app/core/api/models/user.model';

export const ME_ID = '00000000-0000-4000-8000-000000000001';

interface SeedUser extends UserDto {
  id: string;
  user_name: string;
}

export const USERS: SeedUser[] = [
  {
    id: ME_ID,
    user_name: 'ada',
    email: 'ada@tweebyte.dev',
    biography: 'Building the first program for the Analytical Engine. Notes on enchanting numbers.',
    is_private: false,
    birth_date: '1815-12-10',
    created_at: '2026-05-01T09:12:00',
    profile_picture_id: '10000000-0000-4000-8000-000000000001',
    following: 180,
    followers: 24200
  },
  {
    id: '00000000-0000-4000-8000-000000000002',
    user_name: 'linus',
    email: 'linus@tweebyte.dev',
    biography: 'Just a hobby, won’t be big and professional. Talk is cheap, show me the code.',
    is_private: false,
    created_at: '2026-04-18T14:03:00',
    profile_picture_id: '10000000-0000-4000-8000-000000000002',
    following: 42,
    followers: 512000
  },
  {
    id: '00000000-0000-4000-8000-000000000003',
    user_name: 'grace',
    email: 'grace@tweebyte.dev',
    biography: 'It is easier to ask forgiveness than permission. Retired the nanosecond.',
    is_private: false,
    created_at: '2026-03-30T11:45:00',
    profile_picture_id: '10000000-0000-4000-8000-000000000003',
    following: 97,
    followers: 188000
  },
  {
    id: '00000000-0000-4000-8000-000000000004',
    user_name: 'margaret',
    email: 'margaret@tweebyte.dev',
    biography: 'Wrote the on-board flight software for Apollo. Asynchronous executive enjoyer.',
    is_private: true,
    created_at: '2026-02-12T08:21:00',
    profile_picture_id: '10000000-0000-4000-8000-000000000004',
    following: 64,
    followers: 99000
  },
  {
    id: '00000000-0000-4000-8000-000000000005',
    user_name: 'dijkstra',
    email: 'dijkstra@tweebyte.dev',
    biography: 'Simplicity is prerequisite for reliability. Two problems with distributed computing.',
    is_private: false,
    created_at: '2026-01-08T17:30:00',
    profile_picture_id: '10000000-0000-4000-8000-000000000005',
    following: 11,
    followers: 305000
  },
  {
    // A freshly-registered account: no bio, no join date, no avatar, no posts yet. Renders
    // the profile page's "minimal header" arms (no biography / no Joined date) and its
    // empty-tweets state — states the other, fully-populated seed users never reach.
    id: '00000000-0000-4000-8000-000000000006',
    user_name: 'newcomer',
    email: 'newcomer@tweebyte.dev',
    is_private: false
  }
];

export function summaryOf(u: SeedUser): UserSummaryDto {
  return {
    id: u.id,
    user_name: u.user_name,
    is_private: u.is_private,
    created_at: u.created_at
  };
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

const TWEET_TEXTS: {
  author: number;
  content: string;
  likes: number;
  replies: number;
  rts: number;
  tags?: string[];
  /** Optional attached media ids (renders the bearer-aware media tile). */
  mediaIds?: string[];
  /** Optional explicit age in ms (else a default spread is used). Drives the relative-time pipe. */
  ageMs?: number;
}[] = [
  {
    author: 1,
    content:
      'The Analytical Engine weaves algebraic patterns just as the Jacquard loom weaves flowers and leaves. #computing',
    likes: 1820,
    replies: 142,
    rts: 410,
    tags: ['computing'],
    // Two attachments → exercises the multi-media grid + the bearer-aware media tile.
    mediaIds: ['72000000-0000-4000-8000-000000000001', '72000000-0000-4000-8000-000000000002'],
    ageMs: 30 * MINUTE
  },
  {
    author: 0,
    content:
      'Spent the morning convinced the event loop and the thread pool could be friends. They can. The bearer even survives the swap. #reactive #async',
    likes: 932,
    replies: 64,
    rts: 121,
    tags: ['reactive', 'async'],
    ageMs: 10_000,
    // Reuses one media id from the first post so the MediaService shared-cache branch
    // runs with two independent MediaThumb holders for the same asset.
    mediaIds: ['72000000-0000-4000-8000-000000000001']
  },
  {
    author: 2,
    content: 'Backpressure isn’t a feature you add. It’s a truth you stop ignoring. #webflux',
    likes: 2240,
    replies: 305,
    rts: 690,
    tags: ['webflux'],
    ageMs: 3 * HOUR // hours bucket
  },
  {
    author: 4,
    content:
      'The question of whether machines can think is about as relevant as whether submarines can swim. Still shipping though.',
    likes: 2_400_000,
    replies: 612,
    rts: 1500,
    ageMs: 2 * DAY // days bucket
  },
  {
    author: 3,
    content:
      'A ship in port is safe, but that is not what ships are built for. Sail out to sea and do new things. #shipit',
    likes: 1560,
    replies: 88,
    rts: 240,
    tags: ['shipit'],
    ageMs: 400 * DAY // > 1y → absolute date with year
  },
  {
    author: 0,
    content:
      'Hot take: the most interesting benchmark is the one where both stacks tie on throughput and the reactive one wins on memory. Honest numbers > flattering ones.',
    likes: 770,
    replies: 51,
    rts: 96,
    ageMs: 20 * DAY // > 1w, same year → month/day, no year
  }
];

let tweetCounter = 0;
function tweetId(): string {
  tweetCounter += 1;
  return `20000000-0000-4000-8000-${String(tweetCounter).padStart(12, '0')}`;
}

export const TWEETS: TweetDto[] = TWEET_TEXTS.map((t, idx) => {
  const author = USERS[t.author];
  const id = tweetId();
  const ageMs = t.ageMs ?? (idx + 1) * 37 * MINUTE;
  const created = new Date(Date.now() - ageMs).toISOString().replace('Z', '');
  return {
    id,
    user_id: author.id,
    content: t.content,
    created_at: created,
    media_ids: t.mediaIds ?? [],
    mentions: [],
    hashtags: (t.tags ?? []).map((text, i) => ({
      id: `30000000-0000-4000-8000-${String(idx * 10 + i).padStart(12, '0')}`,
      text
    })),
    likes_count: t.likes,
    replies_count: t.replies,
    retweets_count: t.rts,
    user: summaryOf(author)
  } satisfies TweetDto;
});

/**
 * Extra home-timeline posts beyond the curated TWEETS. Each is returned WITHOUT an embedded
 * `user`, mirroring the real GET /feed contract (the feed read omits the author), so the feed
 * page's author backfill (one batched /users/summaries) and the store's mergeAuthors merge
 * actually run on the initial load.
 *
 * Sized to span TWO pages (page size 20): TWEETS (6) + these (21) = 27 rows, so the first
 * load returns a FULL page and the infinite-scroll sentinel triggers a page-2 loadMore — the
 * second page then exhausts the cohort and shows "all caught up". This makes feed.store
 * loadMore (and its pagination-failure arm) reachable from a real scroll.
 *
 * Only the feed endpoint serves these; profile/detail/search stay on the curated TWEETS.
 */
const FEED_FILLER_LINES = [
  'Shipping a small thing today beats planning a big thing forever.',
  'The cache was the bug. The cache is always the bug. #caching',
  'Rewrote it reactive. Same throughput, less memory. Honest trade. #reactive',
  'A green test suite is a love letter to your future self.',
  'Latency is a feature you feel, throughput is a number you quote.',
  'Idempotency turns a scary retry into a boring one. #resilience',
  'The best abstraction is the one you can delete in an afternoon.',
  'Backwards compatibility is a promise you make to your past self.',
  'A flaky test is worse than no test — it teaches you to ignore red. #testing',
  'Observability is just empathy for the engineer on call at 3am.',
  'The fastest query is the one you never send. Cache, then measure. #caching',
  'Premature optimization is the root of all evil; so is never measuring.',
  'Every distributed system is a single point of failure waiting to be discovered.',
  'Naming things well is half the design. The other half is deleting things. #craft',
  'A good migration is boring. A boring migration is a good migration.',
  'Logs are for humans, metrics are for machines, traces are for both. #observability',
  'Read the error message. Then read it again. It is usually right.',
  'The interface is the contract; the implementation is just a rumor.',
  'Ship behind a flag, measure in the light, delete the flag later. #shipit',
  'Most outages are config changes wearing a deploy costume.',
  'Write the test that would have caught the bug you just fixed.'
];

export const FEED_EXTRA: TweetDto[] = FEED_FILLER_LINES.map((content, i) => {
  const author = USERS[i % 5]; // never the newcomer (no posts)
  const tags = /#(\w+)/.exec(content)?.[1];
  const n = i + 1;
  // No embedded `user` → triggers the author-backfill batch read + mergeAuthors merge.
  return {
    id: `21000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
    user_id: author.id,
    content,
    created_at: new Date(Date.now() - (i + 7) * 41 * MINUTE).toISOString().replace('Z', ''),
    media_ids: [],
    mentions: [],
    hashtags: tags ? [{ id: `31000000-0000-4000-8000-${String(n).padStart(12, '0')}`, text: tags }] : [],
    likes_count: ((i * 13) % 900) + 1,
    replies_count: (i * 3) % 40,
    retweets_count: (i * 7) % 120
  } satisfies TweetDto;
});

/**
 * The full home-timeline cohort served by GET /tweets/{uid}/feed (curated + author-less
 * filler). Computed LIVE each call so a runtime-composed tweet (which the compose handler
 * unshifts into TWEETS) appears at the top on the next feed read.
 *
 * NOTE: the TweetCard "absent field" fallback arms (content ?? '', *_count ?? 0,
 * media_ids ?? [], user?.user_name ?? 'unknown', null timestamp) are exercised via the
 * sparse-tweet GET /tweets/{id} detail scenario, NOT a feed row: a feed row with no content
 * and no author renders focusable profile/content links with no accessible name, which is a
 * genuine a11y gap (axe link-name / focusable-no-name) the feed-wide a11y assertions catch.
 */
export function feedTweets(): TweetDto[] {
  return [...TWEETS, ...FEED_EXTRA];
}

/**
 * A maximally-sparse tweet served only by GET /tweets/{SPARSE_TWEET_ID} (the detail route),
 * never in the feed list. The route has the id, but the DTO intentionally omits id/content/
 * counts/media/user/created_at. This drives the TweetCard + RelativeTime absent-field and
 * owner-action guard arms that fully-populated rows never reach. The detail page is opened
 * directly by deep-link, so it is not part of the feed-wide a11y sweep that an unnamed-link
 * feed row would break.
 */
export const SPARSE_TWEET_ID = '22000000-0000-4000-8000-000000000001';
export const SPARSE_TWEET: TweetDto = {};

/**
 * Detail-only tweet with a valid id but missing interaction counts. This keeps the card
 * accessible while exercising the UI's count-default fallbacks.
 */
export const COUNTLESS_TWEET_ID = '22000000-0000-4000-8000-000000000002';
export const COUNTLESS_TWEET: TweetDto = {
  id: COUNTLESS_TWEET_ID,
  user_id: USERS[1].id,
  user: summaryOf(USERS[1]),
  content: 'A detail-only post with omitted counters still renders as an ordinary post.',
  created_at: (Date.now() - 30_000) as unknown as string,
  media_ids: [],
  mentions: [],
  hashtags: []
};

/** Trending hashtags (GET /tweets/hashtag/popular). */
export const TRENDS: { id: string; text: string; count: number }[] = [
  { id: '40000000-0000-4000-8000-000000000001', text: 'reactive', count: 1_840_000 },
  { id: '40000000-0000-4000-8000-000000000002', text: 'webflux', count: 12200 },
  { id: '40000000-0000-4000-8000-000000000003', text: 'async', count: 9800 },
  { id: '40000000-0000-4000-8000-000000000004', text: 'computing', count: 7600 },
  { id: '40000000-0000-4000-8000-000000000005', text: 'shipit', count: 4300 }
];

export function userById(id: string): SeedUser | undefined {
  return USERS.find((u) => u.id === id);
}
