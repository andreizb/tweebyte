import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { SessionStore } from '../state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let session: InstanceType<typeof SessionStore>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  it('attaches the bearer when a session token exists', () => {
    session.setToken(makeToken('u1'));
    http.get('/x').subscribe();
    const req = httpMock.expectOne('/x');
    expect(req.request.headers.get('Authorization')).toMatch(/^Bearer /);
    req.flush({});
  });

  it('sends no Authorization header when unauthenticated', () => {
    http.get('/x').subscribe();
    const req = httpMock.expectOne('/x');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does not overwrite an Authorization header already set by the caller', () => {
    session.setToken(makeToken('u1'));
    http.get('/x', { headers: new HttpHeaders({ Authorization: 'Bearer preset' }) }).subscribe();
    const req = httpMock.expectOne('/x');
    expect(req.request.headers.get('Authorization')).toBe('Bearer preset');
    req.flush({});
  });
});
