import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MediaApiService } from './media.service';
import { GatewayRegistry } from '../../config/gateway-registry.service';
import { SessionStore } from '../../state/session.store';

describe('MediaApiService', () => {
  let service: MediaApiService;
  let http: HttpTestingController;
  let session: InstanceType<typeof SessionStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MediaApiService, provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(MediaApiService);
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    const registry = TestBed.inject(GatewayRegistry);
    registry.applyConfig({
      activeGatewayId: 'gw',
      gateways: [{ id: 'gw', label: 'Gateway', baseUrl: 'http://gw.test' }],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    });
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('upload: POST multipart /user-service/media', () => {
    service.upload(new File(['x'], 'a.png', { type: 'image/png' })).subscribe();
    const req = http.expectOne('/user-service/media');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
    req.flush({ id: 'm1' });
  });

  it('preview / reveal / exists / download routes', () => {
    service.preview('m1', { password: 'p' }).subscribe();
    http.expectOne('/user-service/media/m1/preview').flush({ id: 'm2' });

    service.reveal('m1', { password: 'p' }).subscribe();
    const rv = http.expectOne('/user-service/media/m1/reveal');
    expect(rv.request.responseType).toBe('blob');
    rv.flush(new Blob(['x']));

    service.exists('m1').subscribe();
    http.expectOne('/user-service/media/m1/exists').flush({ exists: true });

    service.download('m1').subscribe();
    const dl = http.expectOne('/user-service/media/m1');
    expect(dl.request.responseType).toBe('blob');
    dl.flush(new Blob(['x']));
  });

  it('blobUrl fetches the active gateway with a bearer and caches the object URL', async () => {
    session.setToken(makeToken('u1'));
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockImplementation(() => Promise.resolve(new Response(new Blob(['bytes']), { status: 200 })));

    const url1 = await new Promise<string>((resolve) => service.blobUrl('m1').subscribe(resolve));
    const url2 = await new Promise<string>((resolve) => service.blobUrl('m1').subscribe(resolve));

    expect(url1).toBe(url2); // cached — one fetch only
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [calledUrl, init] = fetchSpy.mock.calls[0];
    expect(calledUrl).toBe('http://gw.test/user-service/media/m1');
    expect((init as RequestInit).headers).toMatchObject({ Authorization: expect.stringContaining('Bearer ') });
  });

  it('blobUrl errors propagate on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 404 }));
    await expect(
      new Promise((resolve, reject) => service.blobUrl('missing').subscribe({ next: resolve, error: reject }))
    ).rejects.toThrow(/media missing failed: 404/);
  });

  it('invalidate drops the cached entry so the next blobUrl refetches', async () => {
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockImplementation(() => Promise.resolve(new Response(new Blob(['x']), { status: 200 })));
    await new Promise<string>((r) => service.blobUrl('m1').subscribe(r));
    service.invalidate('m1');
    await new Promise<string>((r) => service.blobUrl('m1').subscribe(r));
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it('release revokes the object URL once the last holder lets go (no leak)', async () => {
    stubFetchOk();
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:held');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    // two holders share one fetch + one object URL
    const a = await new Promise<string>((r) => service.acquire('m1').subscribe(r));
    const b = await new Promise<string>((r) => service.acquire('m1').subscribe(r));
    expect(a).toBe('blob:held');
    expect(b).toBe('blob:held');
    expect(createSpy).toHaveBeenCalledTimes(1);

    service.release('m1'); // one holder remains — URL must stay alive
    expect(revokeSpy).not.toHaveBeenCalled();

    service.release('m1'); // last holder — revoke now
    expect(revokeSpy).toHaveBeenCalledWith('blob:held');
  });

  it('release re-acquires a fresh URL after the previous one was revoked', async () => {
    const fetchSpy = stubFetchOk();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:held');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    await new Promise<string>((r) => service.acquire('m1').subscribe(r));
    service.release('m1');
    await new Promise<string>((r) => service.acquire('m1').subscribe(r));
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it('release is a no-op for an id that was never acquired', () => {
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    expect(() => service.release('never')).not.toThrow();
    expect(revokeSpy).not.toHaveBeenCalled();
  });

  it('invalidate revokes the object URL even while a holder still has a ref', async () => {
    stubFetchOk();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:held');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    await new Promise<string>((r) => service.acquire('m1').subscribe(r));
    service.invalidate('m1');
    expect(revokeSpy).toHaveBeenCalledWith('blob:held');
  });
});

function stubFetchOk() {
  return vi
    .spyOn(globalThis, 'fetch')
    .mockImplementation(() => Promise.resolve(new Response(new Blob(['x']), { status: 200 })));
}

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}
