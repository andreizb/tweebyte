/**
 * User domain models — byte-accurate to the backend DTOs.
 *
 * Source of truth: user-service .../model/UserDto.java (+ tweet/interaction variants).
 * Backend serializes snake_case via @JsonProperty and OMITS empty/zero/null fields
 * (@JsonInclude(NON_EMPTY) on the full DTO), so almost everything is optional on read.
 */
import { TweetDto } from './tweet.model';

/**
 * Full user profile — returned by GET /users/{id} and PUT /users/{id}.
 * `@JsonInclude(NON_EMPTY)`: absent field == empty/zero. Treat all as optional on read.
 */
export interface UserDto {
  id?: string;
  user_name?: string;
  email?: string;
  biography?: string;
  is_private?: boolean;
  birth_date?: string; // ISO LocalDate, e.g. "1995-04-21"
  created_at?: string; // ISO LocalDateTime
  profile_picture_id?: string;
  following?: number;
  followers?: number;
  tweets?: TweetDto[];
}

/**
 * Lean identity projection used by tweet-service and interaction-service
 * (GET /users/summary/{id}, embedded in TweetDto.user, etc.).
 * Strictly a subset of UserDto: identity + privacy + created_at only.
 */
export interface UserSummaryDto {
  id?: string;
  user_name?: string;
  is_private?: boolean;
  created_at?: string;
}

/** POST /auth/login body (JSON). */
export interface UserLoginRequest {
  email: string;
  password: string;
}

/**
 * POST /auth/register body — sent as multipart/form-data.
 * NOTE: register/update use camelCase field names (no @JsonProperty on the backend).
 */
export interface UserRegisterRequest {
  userName: string;
  email: string;
  password: string;
  biography?: string;
  birthDate?: string; // ISO LocalDate
  isPrivate?: boolean;
  profilePictureId?: string; // avatar pre-uploaded via POST /media
}

/** PUT /users/{id} body — multipart/form-data, owner-gated. camelCase. */
export interface UserUpdateRequest {
  userName?: string;
  email?: string;
  biography?: string;
  isPrivate?: boolean;
  password?: string;
  birthDate?: string;
  profilePictureId?: string;
}
