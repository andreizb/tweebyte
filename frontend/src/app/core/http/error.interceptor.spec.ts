import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { errorInterceptor } from './error.interceptor';
import { SessionStore } from '../state/session.store';
import { ToastService } from '../state/toast.service';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let session: InstanceType<typeof SessionStore>;
  let toast: ToastService;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    navigate = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { url: '/home', navigate } }
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('401 clears the session and redirects to /login with returnUrl', () => {
    session.setToken(makeToken('u1'));
    http.get('/x').subscribe({ error: () => undefined });
    httpMock.expectOne('/x').flush({ error: 'nope' }, { status: 401, statusText: 'Unauthorized' });
    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/home' } });
  });

  it('403 raises an error toast with the server detail', () => {
    http.get('/x').subscribe({ error: () => undefined });
    httpMock.expectOne('/x').flush({ error: 'forbidden you' }, { status: 403, statusText: 'Forbidden' });
    expect(toast.toasts().some((t) => t.kind === 'error' && t.detail === 'forbidden you')).toBe(true);
  });

  it('404 is silent (caller renders an empty state)', () => {
    http.get('/x').subscribe({ error: () => undefined });
    httpMock.expectOne('/x').flush({ error: 'missing' }, { status: 404, statusText: 'Not Found' });
    expect(toast.toasts().length).toBe(0);
  });

  it('415 and 429 raise the right toast kinds', () => {
    http.get('/a').subscribe({ error: () => undefined });
    httpMock.expectOne('/a').flush({}, { status: 415, statusText: 'Unsupported' });
    expect(toast.toasts().some((t) => t.kind === 'error')).toBe(true);

    http.get('/b').subscribe({ error: () => undefined });
    httpMock.expectOne('/b').flush({}, { status: 429, statusText: 'Too Many' });
    expect(toast.toasts().some((t) => t.kind === 'warn')).toBe(true);
  });

  it('status 0 (network down) is retried, then raises a network-error toast', async () => {
    vi.useFakeTimers();
    http.get('/x').subscribe({ error: () => undefined });
    // status 0 is treated as retriable: initial + 2 retries = 3 attempts.
    httpMock.expectOne('/x').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown' });
    await vi.advanceTimersByTimeAsync(1000);
    httpMock.expectOne('/x').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown' });
    await vi.advanceTimersByTimeAsync(2000);
    httpMock.expectOne('/x').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown' });
    await vi.advanceTimersByTimeAsync(4000);
    expect(toast.toasts().some((t) => t.message === 'Network error')).toBe(true);
  });

  it('retries 5xx with backoff then surfaces the error', async () => {
    vi.useFakeTimers();
    let result: unknown;
    http.get('/x').subscribe({ error: (e) => (result = e) });

    // initial + 2 retries = 3 attempts
    httpMock.expectOne('/x').flush({}, { status: 500, statusText: 'err' });
    await vi.advanceTimersByTimeAsync(1000);
    httpMock.expectOne('/x').flush({}, { status: 500, statusText: 'err' });
    await vi.advanceTimersByTimeAsync(2000);
    httpMock.expectOne('/x').flush({ error: 'boom' }, { status: 500, statusText: 'err' });
    await vi.advanceTimersByTimeAsync(4000);

    expect(result).toBeTruthy();
    expect(toast.toasts().some((t) => t.kind === 'error')).toBe(true);
  });

  it('does NOT retry a 4xx', () => {
    http.get('/x').subscribe({ error: () => undefined });
    httpMock.expectOne('/x').flush({}, { status: 400, statusText: 'bad' });
    httpMock.verify(); // no second request queued
  });

  it('does NOT retry a POST that 5xx (duplicate-write guard) — surfaced immediately', () => {
    // A POST (create tweet/reply/follow) that 5xx'd may have committed server-side; retrying
    // would double-write. It must surface immediately with exactly ONE outbound request.
    let result: unknown;
    http.post('/x', {}).subscribe({ error: (e) => (result = e) });
    httpMock.expectOne('/x').flush({ error: 'boom' }, { status: 500, statusText: 'err' });
    httpMock.verify(); // no second request queued — not retried
    expect(result).toBeTruthy();
    expect(toast.toasts().some((t) => t.kind === 'error')).toBe(true);
  });

  it('does NOT retry a PATCH that 5xx (non-idempotent)', () => {
    http.patch('/x', {}).subscribe({ error: () => undefined });
    httpMock.expectOne('/x').flush({}, { status: 503, statusText: 'err' });
    httpMock.verify(); // no second request queued
  });

  it('DOES retry a GET that 5xx (idempotent) — more than one outbound request', async () => {
    vi.useFakeTimers();
    http.get('/x').subscribe({ error: () => undefined });
    httpMock.expectOne('/x').flush({}, { status: 500, statusText: 'err' });
    await vi.advanceTimersByTimeAsync(1000);
    // A second attempt was queued because GET is idempotent.
    httpMock.expectOne('/x').flush({}, { status: 500, statusText: 'err' });
    await vi.advanceTimersByTimeAsync(2000);
    httpMock.expectOne('/x').flush({}, { status: 500, statusText: 'err' });
    await vi.advanceTimersByTimeAsync(4000);
    httpMock.verify();
  });
});
