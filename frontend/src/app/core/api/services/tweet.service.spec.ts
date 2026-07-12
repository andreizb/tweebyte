import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TweetApiService } from './tweet.service';
import { TweetDto } from '../models/tweet.model';

describe('TweetApiService (contract)', () => {
  let service: TweetApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TweetApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(TweetApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('feed: GET /tweet-service/tweets/{id}/feed with page+size', () => {
    service.feed('u1', 1, 30).subscribe();
    const req = http.expectOne((r) => r.url === '/tweet-service/tweets/u1/feed');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('30');
    req.flush([]);
  });

  it('byId: GET /tweet-service/tweets/{tweetId}', () => {
    service.byId('t1').subscribe();
    http.expectOne('/tweet-service/tweets/t1').flush({ id: 't1' });
  });

  it('byUser: GET /tweet-service/tweets/user/{id} with enrich flag', () => {
    service.byUser('u1', 0, 20, true).subscribe();
    const req = http.expectOne((r) => r.url === '/tweet-service/tweets/user/u1');
    expect(req.request.params.get('enrich')).toBe('true');
    req.flush([]);
  });

  it('search + hashtag search encode the term', () => {
    service.search('a b').subscribe();
    http.expectOne('/tweet-service/tweets/search/a%20b').flush([]);
    service.searchHashtag('c d').subscribe();
    http.expectOne('/tweet-service/tweets/search/hashtag/c%20d').flush([]);
  });

  it('popularHashtags / summaries', () => {
    service.popularHashtags().subscribe();
    http.expectOne('/tweet-service/tweets/hashtag/popular').flush([]);

    // summary(tweetId) returns a full TweetDto, not a TweetSummaryDto (A6).
    let summary: TweetDto | undefined;
    service.summary('t1').subscribe((t) => (summary = t));
    http.expectOne('/tweet-service/tweets/t1/summary').flush({ id: 't1', content: 'hi' });
    expect(summary).toEqual({ id: 't1', content: 'hi' });

    service.userSummary('u1').subscribe();
    http.expectOne('/tweet-service/tweets/user/u1/summary').flush([]);
  });

  it('create: POST /tweet-service/tweets/{userId} with the creation body', () => {
    service.create('u1', { content: 'hello world!!' }).subscribe();
    const req = http.expectOne('/tweet-service/tweets/u1');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'hello world!!' });
    req.flush({ id: 't9' });
  });

  it('update: PUT /tweet-service/tweets/{uid}/{tid} returns no body (204 Void)', () => {
    let emitted: unknown = 'unset';
    service.update('u1', 't1', { content: 'edited content' }).subscribe((v) => (emitted = v));
    const req = http.expectOne('/tweet-service/tweets/u1/t1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ content: 'edited content' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    // The backend echoes no DTO; the stream completes with an empty (null) value.
    expect(emitted).toBeNull();
  });

  it('delete: DELETE /tweet-service/tweets/{uid}/{tid}', () => {
    service.delete('u1', 't1').subscribe();
    const req = http.expectOne('/tweet-service/tweets/u1/t1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
