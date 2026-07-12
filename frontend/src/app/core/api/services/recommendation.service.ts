import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HashtagDto } from '../models/tweet.model';
import { UserSummaryDto } from '../models/user.model';

/** Recommendations API (interaction-service): who-to-follow + trends. */
@Injectable({ providedIn: 'root' })
export class RecommendationApiService {
  private readonly http = inject(HttpClient);

  whoToFollow(userId: string): Observable<UserSummaryDto[]> {
    return this.http.get<UserSummaryDto[]>(
      `/interaction-service/recommendations/${userId}/follow`
    );
  }

  trends(): Observable<HashtagDto[]> {
    return this.http.get<HashtagDto[]>('/interaction-service/recommendations/hashtags');
  }
}

export { RecommendationApiService as RecommendationService };
