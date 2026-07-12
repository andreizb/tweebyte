import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  LikeDto,
  ReplyCreateRequest,
  ReplyDto,
  ReplyUpdateRequest,
  RetweetCreateRequest,
  RetweetDto,
  RetweetUpdateRequest,
  TweetInteractionsEntryDto,
  UuidCountMap,
  UuidReplyMap
} from '../models/interaction.model';

/** Likes, replies, retweets, and batched feed enrichment (interaction-service). */
@Injectable({ providedIn: 'root' })
export class InteractionApiService {
  private readonly http = inject(HttpClient);

  /** Batched counts + top_reply for a whole timeline page in one call. */
  enrich(tweetIds: string[]): Observable<TweetInteractionsEntryDto[]> {
    return this.http.post<TweetInteractionsEntryDto[]>(
      '/interaction-service/tweets/interactions',
      tweetIds
    );
  }

  // Likes
  userLikes(userId: string, page = 0, size = 10): Observable<LikeDto[]> {
    return this.http.get<LikeDto[]>(`/interaction-service/likes/user/${userId}`, {
      params: pageParams(page, size)
    });
  }

  tweetLikes(tweetId: string, page = 0, size = 10): Observable<LikeDto[]> {
    return this.http.get<LikeDto[]>(`/interaction-service/likes/tweet/${tweetId}`, {
      params: pageParams(page, size)
    });
  }

  tweetLikeCount(tweetId: string): Observable<number> {
    return this.http.get<number>(`/interaction-service/likes/${tweetId}/count`);
  }

  tweetLikeCounts(tweetIds: string[]): Observable<UuidCountMap> {
    return this.http.post<UuidCountMap>('/interaction-service/likes/counts', tweetIds);
  }

  likeTweet(userId: string, tweetId: string): Observable<LikeDto> {
    return this.http.post<LikeDto>(
      `/interaction-service/likes/${userId}/tweets/${tweetId}`,
      {}
    );
  }

  unlikeTweet(userId: string, tweetId: string): Observable<void> {
    return this.http.delete<void>(`/interaction-service/likes/${userId}/tweets/${tweetId}`);
  }

  likeReply(userId: string, replyId: string): Observable<LikeDto> {
    return this.http.post<LikeDto>(
      `/interaction-service/likes/${userId}/replies/${replyId}`,
      {}
    );
  }

  unlikeReply(userId: string, replyId: string): Observable<void> {
    return this.http.delete<void>(`/interaction-service/likes/${userId}/replies/${replyId}`);
  }

  // Replies
  createReply(userId: string, body: ReplyCreateRequest): Observable<ReplyDto> {
    return this.http.post<ReplyDto>(`/interaction-service/replies/${userId}`, body);
  }

  updateReply(userId: string, replyId: string, body: ReplyUpdateRequest): Observable<void> {
    return this.http.put<void>(`/interaction-service/replies/${userId}/${replyId}`, body);
  }

  deleteReply(userId: string, replyId: string): Observable<void> {
    return this.http.delete<void>(`/interaction-service/replies/${userId}/${replyId}`);
  }

  repliesForTweet(tweetId: string, page = 0, size = 10): Observable<ReplyDto[]> {
    return this.http.get<ReplyDto[]>(`/interaction-service/replies/tweet/${tweetId}`, {
      params: pageParams(page, size)
    });
  }

  replyCount(tweetId: string): Observable<number> {
    return this.http.get<number>(`/interaction-service/replies/tweet/${tweetId}/count`);
  }

  topReply(tweetId: string): Observable<ReplyDto> {
    return this.http.get<ReplyDto>(`/interaction-service/replies/tweet/${tweetId}/top`);
  }

  replyCounts(tweetIds: string[]): Observable<UuidCountMap> {
    return this.http.post<UuidCountMap>('/interaction-service/replies/tweet/counts', tweetIds);
  }

  topReplies(tweetIds: string[]): Observable<UuidReplyMap> {
    return this.http.post<UuidReplyMap>('/interaction-service/replies/tweet/top', tweetIds);
  }

  // Retweets
  createRetweet(userId: string, body: RetweetCreateRequest): Observable<RetweetDto> {
    return this.http.post<RetweetDto>(`/interaction-service/retweets/${userId}`, body);
  }

  updateRetweet(userId: string, retweetId: string, body: RetweetUpdateRequest): Observable<void> {
    return this.http.put<void>(`/interaction-service/retweets/${userId}/${retweetId}`, body);
  }

  deleteRetweet(userId: string, retweetId: string): Observable<void> {
    return this.http.delete<void>(`/interaction-service/retweets/${userId}/${retweetId}`);
  }

  retweetsByUser(userId: string, page = 0, size = 10): Observable<RetweetDto[]> {
    return this.http.get<RetweetDto[]>(`/interaction-service/retweets/user/${userId}`, {
      params: pageParams(page, size)
    });
  }

  retweetsOfTweet(tweetId: string, page = 0, size = 10): Observable<RetweetDto[]> {
    return this.http.get<RetweetDto[]>(`/interaction-service/retweets/tweet/${tweetId}`, {
      params: pageParams(page, size)
    });
  }

  retweetCount(tweetId: string): Observable<number> {
    return this.http.get<number>(`/interaction-service/retweets/tweet/${tweetId}/count`);
  }

  retweetCounts(tweetIds: string[]): Observable<UuidCountMap> {
    return this.http.post<UuidCountMap>('/interaction-service/retweets/tweet/counts', tweetIds);
  }
}

function pageParams(page: number, size: number): HttpParams {
  return new HttpParams().set('page', page).set('size', size);
}

export { InteractionApiService as InteractionService };
