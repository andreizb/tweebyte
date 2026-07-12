/**
 * INTENTIONALLY RETAINED — not dead code. This SSE-over-POST client (and its
 * AiPromptRequest/AiBufferedResponse models) is the "reactive client matters" artifact and
 * is kept, fully tested, ahead of the AI-summarize UI. The /ai route is an M0 placeholder
 * today; this service is the wiring that page will consume. Do not delete on a dead-code sweep.
 */
import { inject, Injectable } from '@angular/core';
import { GatewayRegistry } from '../../config/gateway-registry.service';
import { SessionStore } from '../../state/session.store';
import { AiBufferedResponse, AiPromptRequest } from '../models/tweet.model';

/** A single decoded event from the AI stream. */
export type AiStreamEvent =
  | { type: 'token'; value: string }
  | { type: 'tool'; name: string }
  | { type: 'done' }
  | { type: 'error'; message: string };

export interface AiStreamHandle {
  /** Async iterator of decoded events (token / tool / done / error). */
  events: AsyncIterable<AiStreamEvent>;
  /** Cancel the stream (aborts the fetch). */
  cancel(): void;
}

export type AiStreamKind = 'summarize' | 'summarize-with-tool';

/**
 * SSE-over-POST client — the reason a "reactive client" matters here.
 *
 * All AI endpoints are POST with a `{prompt}` body that respond `text/event-stream`.
 * Browser EventSource is GET-only, so it is UNUSABLE; we read `response.body` via a
 * ReadableStream reader, parse `data:` frames, surface mid-stream `[tool:<name>]` frames
 * as tool events, and cancel via AbortController. {@link buffered} is the JSON fallback.
 */
@Injectable({ providedIn: 'root' })
export class AiStreamService {
  private readonly registry = inject(GatewayRegistry);
  private readonly session = inject(SessionStore);

  /** Open a streaming summary. The caller consumes `handle.events`. */
  stream(
    userId: string,
    conversationId: string,
    prompt: string,
    kind: AiStreamKind = 'summarize'
  ): AiStreamHandle {
    const controller = new AbortController();
    const url = this.endpoint(userId, conversationId, kind);
    const body: AiPromptRequest = { prompt };
    const init: RequestInit = {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify(body),
      signal: controller.signal
    };

    const events = this.consume(url, init, controller);
    return { events, cancel: () => controller.abort() };
  }

  /** Buffered (non-streaming) fallback: POST -> { response }. */
  async buffered(userId: string, conversationId: string, prompt: string): Promise<string> {
    const url = this.endpoint(userId, conversationId, 'buffered');
    const body: AiPromptRequest = { prompt };
    const res = await fetch(url, {
      method: 'POST',
      headers: this.headers('application/json'),
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      throw new Error(`AI buffered failed: ${res.status}`);
    }
    const parsed = (await res.json().catch(() => ({}))) as Partial<AiBufferedResponse>;
    return parsed.response ?? '';
  }

  /** Scripted dev stream (no LLM). SSE-over-GET. */
  mockStream(userId: string, conversationId: string, tokens = 60, itlMs = 35): AiStreamHandle {
    const controller = new AbortController();
    const base = this.registry.activeBaseUrl();
    const url =
      `${base}/tweet-service/tweets/ai/users/${userId}/conversations/${conversationId}` +
      `/mock-stream?tokens=${tokens}&itlMs=${itlMs}`;
    const init: RequestInit = { method: 'GET', headers: this.headers(), signal: controller.signal };
    return { events: this.consume(url, init, controller), cancel: () => controller.abort() };
  }

  // ---- internals ----

  private endpoint(userId: string, conversationId: string, leaf: string): string {
    const base = this.registry.activeBaseUrl();
    return `${base}/tweet-service/tweets/ai/users/${userId}/conversations/${conversationId}/${leaf}`;
  }

  private headers(accept = 'text/event-stream'): Record<string, string> {
    const token = this.session.token();
    const h: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: accept
    };
    if (token) {
      h['Authorization'] = `Bearer ${token}`;
    }
    return h;
  }

  /** Read the body stream, parse SSE `data:` frames, yield typed events. */
  private async *consume(
    url: string,
    init: RequestInit,
    controller: AbortController
  ): AsyncIterable<AiStreamEvent> {
    let res: Response;
    try {
      res = await fetch(url, init);
    } catch (e) {
      yield { type: 'error', message: abortMessage(e) };
      return;
    }
    if (!res.ok || !res.body) {
      yield { type: 'error', message: `stream HTTP ${res.status}` };
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });

        // SSE frames are separated by a blank line.
        let sep: number;
        while ((sep = indexOfFrameBoundary(buffer)) !== -1) {
          const rawFrame = buffer.slice(0, sep);
          buffer = buffer.slice(sep).replace(/^(\r?\n){1,2}/, '');
          const data = parseDataPayload(rawFrame);
          if (data === null) {
            continue;
          }
          const ev = classify(data);
          if (ev) {
            yield ev;
          }
        }
      }
      // Flush any trailing frame without a terminating blank line.
      const tail = parseDataPayload(buffer);
      if (tail !== null) {
        const ev = classify(tail);
        if (ev) {
          yield ev;
        }
      }
      yield { type: 'done' };
    } catch (e) {
      if (controller.signal.aborted) {
        yield { type: 'done' };
      } else {
        yield { type: 'error', message: abortMessage(e) };
      }
    } finally {
      reader.releaseLock();
    }
  }
}

const TOOL_FRAME = /^\[tool:([^\]]+)\]\s*$/;

function classify(data: string): AiStreamEvent | null {
  const tool = data.match(TOOL_FRAME);
  if (tool) {
    return { type: 'tool', name: tool[1] };
  }
  if (data.length === 0) {
    return null;
  }
  return { type: 'token', value: data };
}

/** Return the index of the next blank-line frame boundary, or -1. */
function indexOfFrameBoundary(buffer: string): number {
  const lf = buffer.indexOf('\n\n');
  const crlf = buffer.indexOf('\r\n\r\n');
  if (lf === -1) {
    return crlf;
  }
  if (crlf === -1) {
    return lf;
  }
  return Math.min(lf, crlf);
}

/** Concatenate `data:` lines within a frame (per the SSE spec). Returns null if none. */
function parseDataPayload(frame: string): string | null {
  const lines = frame.split(/\r?\n/);
  const dataLines = lines.filter((l) => l.startsWith('data:'));
  if (dataLines.length === 0) {
    return null;
  }
  return dataLines.map((l) => l.slice(5).replace(/^ /, '')).join('\n');
}

function abortMessage(e: unknown): string {
  if (e instanceof DOMException && e.name === 'AbortError') {
    return 'cancelled';
  }
  return e instanceof Error ? e.message : 'stream error';
}
