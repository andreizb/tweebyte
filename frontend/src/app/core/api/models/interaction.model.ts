/**
 * Interaction domain models — byte-accurate to interaction-service DTOs.
 *
 * Source of truth: interaction-service .../model/*.java
 * Likes / Replies / Retweets, plus the batched feed-enrichment shape
 * (POST /tweets/interactions => TweetInteractionsEntryDto[]).
 */
import { UserSummaryDto } from './user.model';
import { TweetDto } from './tweet.model';

/** Rich reply (interaction-service) — adds `media_ids` vs the embedded tweet-service shape. */
export interface ReplyDto {
  id?: string;
  user_id?: string;
  user_name?: string;
  content?: string;
  created_at?: string;
  likes_count?: number;
  likes?: LikeDto[];
  media_ids?: string[];
}

/** Full like (interaction-service): includes the user and the target tweet. NON_NULL. */
export interface LikeDto {
  id?: string;
  user?: UserSummaryDto;
  tweet?: TweetDto;
  created_at?: string;
}

/** Retweet / quote (interaction-service). `content` present => quote tweet. NON_NULL. */
export interface RetweetDto {
  id?: string;
  created_at?: string;
  content?: string;
  user?: UserSummaryDto;
  tweet?: TweetDto;
  media_ids?: string[];
}

/** POST /replies body. */
export interface ReplyCreateRequest {
  tweet_id: string;
  user_id: string;
  content: string;
  media_ids?: string[];
}

/** PUT /replies/{user_id}/{reply_id} body. */
export interface ReplyUpdateRequest {
  content: string;
}

/** POST /retweets body. content omitted/empty => plain retweet; present => quote. */
export interface RetweetCreateRequest {
  original_tweet_id: string;
  retweeter_id: string;
  content?: string;
  media_ids?: string[];
}

/** PUT /retweets/{user_id}/{retweet_id} body. */
export interface RetweetUpdateRequest {
  content?: string;
}

/**
 * One row of the BATCHED enrichment call POST /tweets/interactions.
 * Lets a whole timeline page hydrate counts in a single request.
 */
export interface TweetInteractionsEntryDto {
  tweet_id: string;
  likes: number;
  replies: number;
  retweets: number;
  top_reply?: ReplyDto;
}

/** Batch count endpoints return a JSON object keyed by tweet UUID. */
export type UuidCountMap = Record<string, number>;

/** Batch top-reply endpoint returns a JSON object keyed by tweet UUID. */
export type UuidReplyMap = Record<string, ReplyDto | null>;
