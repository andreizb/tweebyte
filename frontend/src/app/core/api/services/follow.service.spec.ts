import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FollowApiService } from './follow.service';

describe('FollowApiService (contract)', () => {
  let service: FollowApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FollowApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(FollowApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('followers (JSON) and following (arraybuffer)', () => {
    service.followers('u1').subscribe();
    http.expectOne('/interaction-service/follows/u1/followers').flush([]);
    service.followingBytes('u1').subscribe();
    const req = http.expectOne('/interaction-service/follows/u1/following');
    expect(req.request.responseType).toBe('arraybuffer');
    req.flush(new ArrayBuffer(0));
  });

  it('counts: followers/following/combined', () => {
    service.followersCount('u1').subscribe();
    http.expectOne('/interaction-service/follows/u1/followers/count').flush(1);
    service.followingCount('u1').subscribe();
    http.expectOne('/interaction-service/follows/u1/following/count').flush(2);
    service.counts('u1').subscribe();
    http.expectOne('/interaction-service/follows/u1/counts').flush({ followers: 1, following: 2 });
  });

  it('followersIdentifiers + profileInteractions one-shot', () => {
    service.followersIdentifiers('u1').subscribe();
    http.expectOne('/interaction-service/follows/u1/followers/identifiers').flush([]);
    service.profileInteractions('u1', ['t1', 't2']).subscribe();
    const req = http.expectOne('/interaction-service/follows/u1/profile-interactions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(['t1', 't2']);
    req.flush({});
  });

  it('pendingRequests', () => {
    service.pendingRequests('u1').subscribe();
    http.expectOne('/interaction-service/follows/u1/requests').flush([]);
  });

  it('follow / unfollow / respondToRequest', () => {
    service.follow('u1', 'u2').subscribe();
    const f = http.expectOne('/interaction-service/follows/u1/u2');
    expect(f.request.method).toBe('POST');
    f.flush(null);

    service.unfollow('u1', 'u2').subscribe();
    const u = http.expectOne('/interaction-service/follows/u1/u2');
    expect(u.request.method).toBe('DELETE');
    u.flush(null);

    service.respondToRequest('u1', 'req1', 'ACCEPTED').subscribe();
    const r = http.expectOne('/interaction-service/follows/u1/req1/ACCEPTED');
    expect(r.request.method).toBe('PUT');
    r.flush(null);
  });
});
