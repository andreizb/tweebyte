/**
 * Tweet domain models — byte-accurate to tweet-service DTOs.
 *
 * Source of truth: tweet-service .../model/TweetDto.java + nested DTOs.
 * The tweet-service TweetDto is the CANONICAL CARD MODEL — it carries everything a
 * TweetCard needs (counts, media ids, mentions, hashtags, top_reply, embedded user)
 * with no extra fan-out. `@JsonInclude(NON_EMPTY)` => absent == empty/zero.
 */
import { UserSummaryDto } from './user.model';

export interface MentionDto {
  id?: string;
  user_id?: string;
  text?: string;
}

export interface HashtagDto {
  id?: string;
  text?: string;
  count?: number; // present on trends (GET /tweets/hashtag/popular), absent on a tweet
}

/** A like reference embedded in a reply (NON_NULL => `{ id }` only). */
export interface LikeRefDto {
  id?: string;
}

/**
 * Reply as embedded in a tweet (tweet-service shape).
 * The richer interaction-service ReplyDto adds `media_ids` — see reply.model.ts.
 */
export interface EmbeddedReplyDto {
  id?: string;
  user_id?: string;
  user_name?: string;
  content?: string;
  created_at?: string;
  likes_count?: number;
  likes?: LikeRefDto[];
}

/**
 * Canonical tweet card model (tweet-service GET /tweets/{id}/feed, /tweets/{tweetId}, etc.).
 * `media_ids` is a JSON array of media UUIDs (each rendered via the bearer-protected
 * GET /media/{id} blob). `user` is the lean UserSummaryDto.
 */
export interface TweetDto {
  id?: string;
  user_id?: string;
  content?: string;
  created_at?: string;
  media_ids?: string[];
  mentions?: MentionDto[];
  hashtags?: HashtagDto[];
  likes_count?: number;
  replies_count?: number;
  retweets_count?: number;
  top_reply?: EmbeddedReplyDto;
  replies?: EmbeddedReplyDto[];
  user?: UserSummaryDto;
}

/** POST /tweets/{id} body. content >= 10 chars (backend-validated). */
export interface TweetCreationRequest {
  content: string;
  media_ids?: string[];
}

/** PUT /tweets/{uid}/{tid} body. */
export interface TweetUpdateRequest {
  content: string;
}

/** GET /tweets/{id}/summary and /tweets/user/{id}/summary. */
export interface TweetSummaryDto {
  id?: string;
  hashtags?: string[];
  mentions?: string[];
}

/** POST /tweets/ai/... request body. */
export interface AiPromptRequest {
  prompt: string;
}

/** POST /tweets/ai/.../buffered response body. */
export interface AiBufferedResponse {
  response: string;
}
