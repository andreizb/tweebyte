import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RecommendationApiService } from './recommendation.service';

describe('RecommendationApiService (contract)', () => {
  let service: RecommendationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [RecommendationApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(RecommendationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('whoToFollow: GET /interaction-service/recommendations/{id}/follow', () => {
    service.whoToFollow('u1').subscribe();
    const req = http.expectOne('/interaction-service/recommendations/u1/follow');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('trends: GET /interaction-service/recommendations/hashtags', () => {
    service.trends().subscribe();
    http.expectOne('/interaction-service/recommendations/hashtags').flush([]);
  });
});
