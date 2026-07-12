import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  HashtagDto,
  TweetCreationRequest,
  TweetDto,
  TweetSummaryDto,
  TweetUpdateRequest
} from '../models/tweet.model';

/** Tweets / feed / search API (tweet-service). */
@Injectable({ providedIn: 'root' })
export class TweetApiService {
  private readonly http = inject(HttpClient);

  /** Home timeline for a user. */
  feed(userId: string, page = 0, size = 20): Observable<TweetDto[]> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<TweetDto[]>(`/tweet-service/tweets/${userId}/feed`, { params });
  }

  byId(tweetId: string): Observable<TweetDto> {
    return this.http.get<TweetDto>(`/tweet-service/tweets/${tweetId}`);
  }

  byUser(userId: string, page = 0, size = 20, enrich = true): Observable<TweetDto[]> {
    const params = new HttpParams().set('page', page).set('size', size).set('enrich', enrich);
    return this.http.get<TweetDto[]>(`/tweet-service/tweets/user/${userId}`, { params });
  }

  search(term: string): Observable<TweetDto[]> {
    return this.http.get<TweetDto[]>(`/tweet-service/tweets/search/${encodeURIComponent(term)}`);
  }

  searchHashtag(term: string): Observable<TweetDto[]> {
    return this.http.get<TweetDto[]>(
      `/tweet-service/tweets/search/hashtag/${encodeURIComponent(term)}`
    );
  }

  popularHashtags(): Observable<HashtagDto[]> {
    return this.http.get<HashtagDto[]>('/tweet-service/tweets/hashtag/popular');
  }

  /** GET /tweets/{tweetId}/summary returns a full TweetDto (not a TweetSummaryDto). */
  summary(tweetId: string): Observable<TweetDto> {
    return this.http.get<TweetDto>(`/tweet-service/tweets/${tweetId}/summary`);
  }

  userSummary(userId: string): Observable<TweetSummaryDto[]> {
    return this.http.get<TweetSummaryDto[]>(`/tweet-service/tweets/user/${userId}/summary`);
  }

  /** Owner-gated compose. content >= 10 chars. */
  create(userId: string, body: TweetCreationRequest): Observable<TweetDto> {
    return this.http.post<TweetDto>(`/tweet-service/tweets/${userId}`, body);
  }

  /**
   * Owner-gated edit. PUT /tweets/{userId}/{tweetId} responds 204 No Content
   * (TweetController returns Void) — there is no echoed DTO, so callers must
   * re-read the tweet to see the saved state.
   */
  update(userId: string, tweetId: string, body: TweetUpdateRequest): Observable<void> {
    return this.http.put<void>(`/tweet-service/tweets/${userId}/${tweetId}`, body);
  }

  /** Owner-gated delete. */
  delete(userId: string, tweetId: string): Observable<void> {
    return this.http.delete<void>(`/tweet-service/tweets/${userId}/${tweetId}`);
  }
}

export { TweetApiService as TweetService };
