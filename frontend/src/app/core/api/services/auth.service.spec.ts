import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthApiService } from './auth.service';

describe('AuthApiService (contract)', () => {
  let service: AuthApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('POSTs JSON to the gateway-relative login route', () => {
    service.login({ email: 'ada@x.dev', password: 'pw' }).subscribe();
    const req = http.expectOne('/user-service/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'ada@x.dev', password: 'pw' });
    expect(req.request.url.startsWith('/')).toBe(true); // relative — gateway-agnostic
    req.flush({ token: 'jwt' });
  });

  it('register sends multipart form-data with all provided fields', () => {
    service
      .register({
        userName: 'ada',
        email: 'ada@x.dev',
        password: 'pw',
        biography: 'bio',
        birthDate: '1990-01-01',
        isPrivate: true,
        profilePictureId: 'pic-1'
      })
      .subscribe();
    const req = http.expectOne('/user-service/auth/register');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('userName')).toBe('ada');
    expect(body.get('biography')).toBe('bio');
    expect(body.get('birthDate')).toBe('1990-01-01');
    expect(body.get('isPrivate')).toBe('true');
    expect(body.get('profilePictureId')).toBe('pic-1');
    req.flush({ token: 'jwt' });
  });

  it('register omits optional fields when not provided (biography always sent, defaulting to empty)', () => {
    service.register({ userName: 'ada', email: 'ada@x.dev', password: 'pw' }).subscribe();
    const req = http.expectOne('/user-service/auth/register');
    const body = req.request.body as FormData;
    // biography is NOT NULL on the backend, so it is always sent (empty when not provided).
    expect(body.get('biography')).toBe('');
    expect(body.has('birthDate')).toBe(false);
    expect(body.has('isPrivate')).toBe(false);
    expect(body.has('profilePictureId')).toBe(false);
    req.flush({ token: 'jwt' });
  });
});
