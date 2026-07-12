import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  FollowCountsDto,
  FollowDto,
  FollowIdentifierListDto,
  FollowStatus,
  ProfileInteractionsDto
} from '../models/follow.model';

/**
 * Follows API (interaction-service).
 *
 * /follows/{id}/following returns compact bytes; /followers is JSON.
 */
@Injectable({ providedIn: 'root' })
export class FollowApiService {
  private readonly http = inject(HttpClient);

  followers(userId: string): Observable<FollowDto[]> {
    return this.http.get<FollowDto[]>(`/interaction-service/follows/${userId}/followers`);
  }

  followingBytes(userId: string): Observable<ArrayBuffer> {
    return this.http.get(`/interaction-service/follows/${userId}/following`, {
      responseType: 'arraybuffer'
    });
  }

  followersCount(userId: string): Observable<number> {
    return this.http.get<number>(`/interaction-service/follows/${userId}/followers/count`);
  }

  followingCount(userId: string): Observable<number> {
    return this.http.get<number>(`/interaction-service/follows/${userId}/following/count`);
  }

  followersIdentifiers(userId: string): Observable<FollowIdentifierListDto> {
    return this.http.get<FollowIdentifierListDto>(
      `/interaction-service/follows/${userId}/followers/identifiers`
    );
  }

  counts(userId: string): Observable<FollowCountsDto> {
    return this.http.get<FollowCountsDto>(`/interaction-service/follows/${userId}/counts`);
  }

  /** Profile one-shot: counts + batched interaction rows for that profile's tweets. */
  profileInteractions(userId: string, tweetIds: string[]): Observable<ProfileInteractionsDto> {
    return this.http.post<ProfileInteractionsDto>(
      `/interaction-service/follows/${userId}/profile-interactions`,
      tweetIds
    );
  }

  /** Pending follow requests (private accounts). */
  pendingRequests(userId: string): Observable<FollowDto[]> {
    return this.http.get<FollowDto[]>(`/interaction-service/follows/${userId}/requests`);
  }

  /** Owner-gated follow. */
  follow(userId: string, followedId: string): Observable<void> {
    return this.http.post<void>(
      `/interaction-service/follows/${userId}/${followedId}`,
      {}
    );
  }

  /** Owner-gated accept/reject of a pending request. */
  respondToRequest(userId: string, requestId: string, status: FollowStatus): Observable<void> {
    return this.http.put<void>(
      `/interaction-service/follows/${userId}/${requestId}/${status}`,
      {}
    );
  }

  /** Owner-gated unfollow. */
  unfollow(userId: string, followedId: string): Observable<void> {
    return this.http.delete<void>(`/interaction-service/follows/${userId}/${followedId}`);
  }
}

export { FollowApiService as FollowService };
