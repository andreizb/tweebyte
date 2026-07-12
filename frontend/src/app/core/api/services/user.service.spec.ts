import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserApiService } from './user.service';

describe('UserApiService (contract)', () => {
  let service: UserApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(UserApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GET /user-service/users/{id}', () => {
    service.get('u1').subscribe();
    const req = http.expectOne('/user-service/users/u1');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'u1' });
  });

  it('GET /user-service/users/summary/{id}', () => {
    service.summary('u1').subscribe();
    http.expectOne('/user-service/users/summary/u1').flush({ id: 'u1' });
  });

  it('POST /user-service/users/summaries with the id array body', () => {
    service.summaries(['a', 'b']).subscribe();
    const req = http.expectOne('/user-service/users/summaries');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(['a', 'b']);
    req.flush([]);
  });

  it('GET summary by name URL-encodes the name', () => {
    service.summaryByName('a b').subscribe();
    http.expectOne('/user-service/users/summary/name/a%20b').flush({});
  });

  it('GET search passes page+size params and encodes the term', () => {
    service.search('a b', 2, 50).subscribe();
    const req = http.expectOne((r) => r.url === '/user-service/users/search/a%20b');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush([]);
  });

  it('PUT update sends multipart form-data, skipping undefined/null, and completes on 204', () => {
    let completed = false;
    service.update('u1', { userName: 'ada', biography: undefined }).subscribe({ complete: () => (completed = true) });
    const req = http.expectOne('/user-service/users/u1');
    expect(req.request.method).toBe('PUT');
    const body = req.request.body as FormData;
    expect(body.get('userName')).toBe('ada');
    expect(body.has('biography')).toBe(false);
    // PUT /users/{id} responds 204 No Content (no echoed DTO).
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(completed).toBe(true);
  });

  it('DELETE /user-service/users/{id}', () => {
    service.delete('u1').subscribe();
    const req = http.expectOne('/user-service/users/u1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
