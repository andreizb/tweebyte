/**
 * Follow domain models — byte-accurate to interaction-service DTOs.
 *
 * Source of truth: interaction-service .../model/{FollowDto,FollowingEntryDto,
 * FollowCountsDto,ProfileInteractionsDto}.java
 *
 * IMPORTANT WIRE NOTE: GET /follows/{id}/followers returns FollowDto[]; GET
 * /follows/{id}/following returns a compact byte[] payload (opaque, NOT JSON).
 * Use the JSON sibling endpoints for UI reads where possible.
 */
import { TweetInteractionsEntryDto } from './interaction.model';

export type FollowStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

/** A follow edge / follow-request (interaction-service). NON_NULL. */
export interface FollowDto {
  id?: string;
  user_name?: string;
  follower_id?: string;
  followed_id?: string;
  created_at?: string;
  status?: FollowStatus;
}

/** Compact following row (record). NON_NULL. */
export interface FollowingEntryDto {
  followed_id?: string;
  user_name?: string;
  created_at?: string;
}

/** GET /follows/{id}/counts and /follows/{id}/followers|following/count wrapper. */
export interface FollowCountsDto {
  followers: number;
  following: number;
}

/**
 * POST /follows/{id}/profile-interactions — the profile one-shot: counts plus the
 * batched interaction rows for that profile's tweets, in a single round-trip.
 */
export interface ProfileInteractionsDto {
  follow_counts?: FollowCountsDto;
  tweet_interactions?: TweetInteractionsEntryDto[];
}

/** GET /follows/{id}/followers/identifiers response. */
export type FollowIdentifierListDto = string[];
