import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InteractionApiService } from './interaction.service';

describe('InteractionApiService (contract)', () => {
  let service: InteractionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [InteractionApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(InteractionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('enrich: POST /interaction-service/tweets/interactions with id array', () => {
    service.enrich(['t1', 't2']).subscribe();
    const req = http.expectOne('/interaction-service/tweets/interactions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(['t1', 't2']);
    req.flush([]);
  });

  it('likeTweet / unlikeTweet hit the owner-scoped routes', () => {
    service.likeTweet('u1', 't1').subscribe();
    const like = http.expectOne('/interaction-service/likes/u1/tweets/t1');
    expect(like.request.method).toBe('POST');
    like.flush({ id: 'l1' });

    service.unlikeTweet('u1', 't1').subscribe();
    const unlike = http.expectOne('/interaction-service/likes/u1/tweets/t1');
    expect(unlike.request.method).toBe('DELETE');
    unlike.flush(null);
  });

  it('likeReply / unlikeReply', () => {
    service.likeReply('u1', 'r1').subscribe();
    http.expectOne('/interaction-service/likes/u1/replies/r1').flush({ id: 'l2' });
    service.unlikeReply('u1', 'r1').subscribe();
    http.expectOne('/interaction-service/likes/u1/replies/r1').flush(null);
  });

  it('like counts: single + batch', () => {
    service.tweetLikeCount('t1').subscribe();
    http.expectOne('/interaction-service/likes/t1/count').flush(5);
    service.tweetLikeCounts(['t1', 't2']).subscribe();
    const batch = http.expectOne('/interaction-service/likes/counts');
    expect(batch.request.body).toEqual(['t1', 't2']);
    batch.flush({});
  });

  it('like lists with paging', () => {
    service.userLikes('u1', 2, 5).subscribe();
    const u = http.expectOne((r) => r.url === '/interaction-service/likes/user/u1');
    expect(u.request.params.get('page')).toBe('2');
    expect(u.request.params.get('size')).toBe('5');
    u.flush([]);
    service.tweetLikes('t1').subscribe();
    http.expectOne((r) => r.url === '/interaction-service/likes/tweet/t1').flush([]);
  });

  it('replies: create/update/delete', () => {
    service.createReply('u1', { tweet_id: 't1', user_id: 'u1', content: 'a reply' }).subscribe();
    const c = http.expectOne('/interaction-service/replies/u1');
    expect(c.request.method).toBe('POST');
    c.flush({ id: 'r1' });

    service.updateReply('u1', 'r1', { content: 'edited' }).subscribe();
    const up = http.expectOne('/interaction-service/replies/u1/r1');
    expect(up.request.method).toBe('PUT');
    up.flush(null);

    service.deleteReply('u1', 'r1').subscribe();
    const d = http.expectOne('/interaction-service/replies/u1/r1');
    expect(d.request.method).toBe('DELETE');
    d.flush(null);
  });

  it('replies: list/count/top + batch', () => {
    service.repliesForTweet('t1').subscribe();
    http.expectOne((r) => r.url === '/interaction-service/replies/tweet/t1').flush([]);
    service.replyCount('t1').subscribe();
    http.expectOne('/interaction-service/replies/tweet/t1/count').flush(2);
    service.topReply('t1').subscribe();
    http.expectOne('/interaction-service/replies/tweet/t1/top').flush({});
    service.replyCounts(['t1']).subscribe();
    http.expectOne('/interaction-service/replies/tweet/counts').flush({});
    service.topReplies(['t1']).subscribe();
    http.expectOne('/interaction-service/replies/tweet/top').flush({});
  });

  it('retweets: create/update/delete + lists + counts', () => {
    service.createRetweet('u1', { original_tweet_id: 't1', retweeter_id: 'u1' }).subscribe();
    const c = http.expectOne('/interaction-service/retweets/u1');
    expect(c.request.method).toBe('POST');
    c.flush({ id: 'rt1' });

    service.updateRetweet('u1', 'rt1', { content: 'quote' }).subscribe();
    http.expectOne('/interaction-service/retweets/u1/rt1').flush(null);

    service.deleteRetweet('u1', 'rt1').subscribe();
    const d = http.expectOne('/interaction-service/retweets/u1/rt1');
    expect(d.request.method).toBe('DELETE');
    d.flush(null);

    service.retweetsByUser('u1').subscribe();
    http.expectOne((r) => r.url === '/interaction-service/retweets/user/u1').flush([]);
    service.retweetsOfTweet('t1').subscribe();
    http.expectOne((r) => r.url === '/interaction-service/retweets/tweet/t1').flush([]);
    service.retweetCount('t1').subscribe();
    http.expectOne('/interaction-service/retweets/tweet/t1/count').flush(3);
    service.retweetCounts(['t1']).subscribe();
    http.expectOne('/interaction-service/retweets/tweet/counts').flush({});
  });
});
