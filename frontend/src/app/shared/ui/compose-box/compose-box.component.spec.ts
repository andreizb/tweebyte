import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ComposeBoxComponent } from './compose-box.component';
import { TweetDto } from '../../../core/api/models/tweet.model';
import { TweetService } from '../../../core/api/services/tweet.service';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../core/state/toast.service';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

const CREATED: TweetDto = { id: 't9', content: 'hello world!!', user_id: 'me' };

/** Render the box with a mock TweetService; optionally authenticate as `me`. */
async function renderBox(create: ReturnType<typeof vi.fn>, authed = true) {
  const result = await render(ComposeBoxComponent, {
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: TweetService, useValue: { create } }
    ]
  });
  if (authed) {
    TestBed.inject(SessionStore).setToken(makeToken('me'));
  }
  return result;
}

describe('ComposeBoxComponent', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders a textarea with the default placeholder', async () => {
    await renderBox(vi.fn());
    const textarea = screen.getByPlaceholderText('What is happening?!');
    expect(textarea.tagName.toLowerCase()).toBe('textarea');
  });

  it('disables Post (canPost false) below MIN_LEN and enables it at/above', async () => {
    const { fixture } = await renderBox(vi.fn());
    const cmp = fixture.componentInstance;

    cmp.text = 'short'; // 5 chars
    cmp.onInput();
    fixture.detectChanges();
    expect(cmp.len()).toBe(5);
    expect(cmp.canPost()).toBe(false);
    expect(screen.getByRole('button', { name: 'Post' }).hasAttribute('disabled')).toBe(true);

    cmp.text = 'hello world!!'; // 13 chars >= 10
    cmp.onInput();
    fixture.detectChanges();
    expect(cmp.len()).toBe(13);
    expect(cmp.canPost()).toBe(true);
    expect(screen.getByRole('button', { name: 'Post' }).hasAttribute('disabled')).toBe(false);
  });

  it('remaining() = MAX_LEN - len and tooShort() only for 0 < len < MIN_LEN', async () => {
    const { fixture } = await renderBox(vi.fn());
    const cmp = fixture.componentInstance;

    // empty: not tooShort, full budget
    expect(cmp.remaining()).toBe(280);
    expect(cmp.tooShort()).toBe(false);

    cmp.text = 'four';
    cmp.onInput();
    fixture.detectChanges();
    expect(cmp.remaining()).toBe(276);
    expect(cmp.tooShort()).toBe(true);

    cmp.text = 'a meaningful sentence';
    cmp.onInput();
    fixture.detectChanges();
    expect(cmp.tooShort()).toBe(false);
    expect(cmp.remaining()).toBe(280 - 21);
  });

  it('post(): calls create(userId, {content}), emits posted, clears, success toast', async () => {
    const create = vi.fn().mockReturnValue(of(CREATED));
    const { fixture } = await renderBox(create);
    const cmp = fixture.componentInstance;
    const success = vi.spyOn(TestBed.inject(ToastService), 'success');

    const posted = vi.fn();
    cmp.posted.subscribe(posted);

    cmp.text = 'hello world!!';
    cmp.onInput();
    fixture.detectChanges();

    cmp.post();

    expect(create).toHaveBeenCalledWith('me', { content: 'hello world!!' });
    // Emits an OPTIMISTIC card built from local state (server returns {id} only), not the
    // raw create response — so the prepended tweet renders author/body/counts immediately.
    expect(posted).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 't9',
        user_id: 'me',
        content: 'hello world!!',
        likes_count: 0,
        replies_count: 0,
        retweets_count: 0
      })
    );
    expect(cmp.text).toBe('');
    expect(cmp.len()).toBe(0);
    expect(cmp.posting()).toBe(false);
    expect(success).toHaveBeenCalledWith('Posted');
  });

  it('post(): trims surrounding whitespace before sending', async () => {
    const create = vi.fn().mockReturnValue(of(CREATED));
    const { fixture } = await renderBox(create);
    const cmp = fixture.componentInstance;

    cmp.text = '   hello world!!   ';
    cmp.onInput();
    fixture.detectChanges();
    cmp.post();

    expect(create).toHaveBeenCalledWith('me', { content: 'hello world!!' });
  });

  it('post(): no-op when not authenticated (no userId)', async () => {
    const create = vi.fn().mockReturnValue(of(CREATED));
    const { fixture } = await renderBox(create, /* authed */ false);
    const cmp = fixture.componentInstance;

    cmp.text = 'hello world!!';
    cmp.onInput();
    fixture.detectChanges();
    cmp.post();

    expect(create).not.toHaveBeenCalled();
    expect(cmp.posting()).toBe(false);
  });

  it('post(): on error shows an error toast and clears posting state', async () => {
    const create = vi.fn().mockReturnValue(throwError(() => new Error('boom')));
    const { fixture } = await renderBox(create);
    const cmp = fixture.componentInstance;
    const error = vi.spyOn(TestBed.inject(ToastService), 'error');

    const posted = vi.fn();
    cmp.posted.subscribe(posted);

    cmp.text = 'hello world!!';
    cmp.onInput();
    fixture.detectChanges();
    cmp.post();

    expect(create).toHaveBeenCalledWith('me', { content: 'hello world!!' });
    expect(error).toHaveBeenCalledWith('Could not post', 'Please try again.');
    expect(posted).not.toHaveBeenCalled();
    expect(cmp.posting()).toBe(false);
  });
});
