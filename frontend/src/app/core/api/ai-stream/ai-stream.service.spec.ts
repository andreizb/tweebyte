import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { AiStreamService, AiStreamEvent } from './ai-stream.service';
import { GatewayRegistry } from '../../config/gateway-registry.service';
import { SessionStore } from '../../state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

/** Build a Response whose body is an SSE stream of the given raw chunks. */
function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const c of chunks) {
        controller.enqueue(encoder.encode(c));
      }
      controller.close();
    }
  });
  return new Response(body, { status, headers: { 'Content-Type': 'text/event-stream' } });
}

async function collect(events: AsyncIterable<AiStreamEvent>): Promise<AiStreamEvent[]> {
  const out: AiStreamEvent[] = [];
  for await (const e of events) {
    out.push(e);
  }
  return out;
}

describe('AiStreamService', () => {
  let service: AiStreamService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [AiStreamService, GatewayRegistry, SessionStore] });
    service = TestBed.inject(AiStreamService);
    TestBed.inject(GatewayRegistry).applyConfig({
      activeGatewayId: 'g',
      gateways: [{ id: 'g', label: 'G', baseUrl: 'http://gw.test' }],
      useMock: false,
      healthPollMs: 0,
      showLatency: false
    });
    TestBed.inject(SessionStore).setToken(makeToken('me'));
  });

  afterEach(() => { vi.restoreAllMocks(); });

  it('parses data frames into token events then done', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(['data: Hello\n\n', 'data: world\n\n']));
    const handle = service.stream('me', 'c1', 'prompt');
    const events = await collect(handle.events);
    expect(events).toEqual([
      { type: 'token', value: 'Hello' },
      { type: 'token', value: 'world' },
      { type: 'done' }
    ]);
  });

  it('surfaces a mid-stream [tool:...] frame as a tool event', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      sseResponse(['data: before\n\n', 'data: [tool:trend-lookup]\n\n', 'data: after\n\n'])
    );
    const events = await collect(service.stream('me', 'c1', 'p', 'summarize-with-tool').events);
    expect(events).toContainEqual({ type: 'tool', name: 'trend-lookup' });
    expect(events.filter((e) => e.type === 'token').map((e) => (e as { value: string }).value)).toEqual([
      'before',
      'after'
    ]);
  });

  it('POSTs to the correct gateway-relative AI endpoint with the bearer', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(['data: x\n\n']));
    await collect(service.stream('me', 'conv-9', 'hi', 'summarize').events);
    const [url, init] = spy.mock.calls[0];
    expect(url).toBe('http://gw.test/tweet-service/tweets/ai/users/me/conversations/conv-9/summarize');
    expect((init as RequestInit).method).toBe('POST');
    expect((init as RequestInit).headers).toMatchObject({ Authorization: expect.stringContaining('Bearer ') });
    expect((init as RequestInit).body).toBe(JSON.stringify({ prompt: 'hi' }));
  });

  it('flushes a trailing frame that has no terminating blank line', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(['data: tail-no-newline']));
    const events = await collect(service.stream('me', 'c1', 'p').events);
    expect(events).toEqual([{ type: 'token', value: 'tail-no-newline' }, { type: 'done' }]);
  });

  it('emits an error event on a non-ok stream response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 500 }));
    const events = await collect(service.stream('me', 'c1', 'p').events);
    expect(events).toEqual([{ type: 'error', message: 'stream HTTP 500' }]);
  });

  it('emits an error event when fetch rejects', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network down'));
    const events = await collect(service.stream('me', 'c1', 'p').events);
    expect(events[0].type).toBe('error');
  });

  it('buffered() returns the JSON response field', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ response: 'the summary' }), { status: 200 })
    );
    await expect(service.buffered('me', 'c1', 'p')).resolves.toBe('the summary');
  });

  it('buffered() throws on a non-ok response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 503 }));
    await expect(service.buffered('me', 'c1', 'p')).rejects.toThrow(/AI buffered failed: 503/);
  });

  it('mockStream() hits the GET mock-stream endpoint with query params', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(sseResponse(['data: a\n\n']));
    await collect(service.mockStream('me', 'c1', 10, 5).events);
    const [url, init] = spy.mock.calls[0];
    expect(String(url)).toContain('/mock-stream?tokens=10&itlMs=5');
    expect((init as RequestInit).method).toBe('GET');
  });

  it('cancel() aborts the fetch (the request carries an aborted signal)', async () => {
    let seenSignal: AbortSignal | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_url, init) => {
      seenSignal = (init as RequestInit).signal ?? undefined;
      return Promise.resolve(sseResponse(['data: a\n\n']));
    });
    const handle = service.stream('me', 'c1', 'p');
    handle.cancel(); // abort BEFORE the lazy generator runs fetch
    // Iterating now performs the (already-aborted) fetch.
    await collect(handle.events).catch(() => undefined);
    expect(seenSignal).toBeDefined();
    expect(seenSignal!.aborted).toBe(true);
  });
});
