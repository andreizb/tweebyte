import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { SearchPage } from './search.page';
import { UserSummaryDto } from '../../core/api/models/user.model';
import { UserService } from '../../core/api/services/user.service';
import { SessionStore } from '../../core/state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

const MATCHES: UserSummaryDto[] = [
  { id: 'u1', user_name: 'grace', is_private: false },
  { id: 'u2', user_name: 'gracehopper', is_private: false }
];

async function renderSearch(search?: ReturnType<typeof vi.fn>) {
  const searchFn = search ?? vi.fn().mockReturnValue(of(MATCHES));
  const userSvc = { search: searchFn };
  const result = await render(SearchPage, {
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: UserService, useValue: userSvc }
    ],
    configureTestBed: (tb) => tb.inject(SessionStore).setToken(makeToken('me'))
  });
  return { ...result, searchFn };
}

describe('SearchPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the search input and an idle prompt', async () => {
    const { fixture } = await renderSearch();
    fixture.detectChanges();
    expect(screen.getByLabelText('Search people')).toBeTruthy();
    expect(screen.getByTestId('search-idle')).toBeTruthy();
  });

  it('debounces input and queries once after the quiet window', async () => {
    const { fixture, searchFn } = await renderSearch();
    const cmp = fixture.componentInstance;

    cmp.onTerm('g');
    cmp.onTerm('gr');
    cmp.onTerm('gra');
    expect(searchFn).not.toHaveBeenCalled(); // still within the debounce window

    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(searchFn).toHaveBeenCalledTimes(1);
    expect(searchFn).toHaveBeenCalledWith('gra');
    expect(screen.getByTestId('search-results')).toBeTruthy();
    expect(screen.getAllByText('grace').length).toBeGreaterThan(0);
  });

  it('does not query for a single character (too short)', async () => {
    const { fixture, searchFn } = await renderSearch();
    fixture.componentInstance.onTerm('g');
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    expect(searchFn).not.toHaveBeenCalled();
  });

  it('shows the empty state when a query returns nothing', async () => {
    const { fixture } = await renderSearch(vi.fn().mockReturnValue(of([])));
    fixture.componentInstance.onTerm('zzz');
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    fixture.detectChanges();
    const empty = screen.getByTestId('search-empty');
    expect(empty).toBeTruthy();
    expect(empty.textContent).toContain('zzz');
  });

  it('shows an error state when the search fails, and recovers on the next query', async () => {
    const searchFn = vi
      .fn()
      .mockReturnValueOnce(throwError(() => new Error('boom')))
      .mockReturnValueOnce(of(MATCHES));
    const { fixture } = await renderSearch(searchFn);

    fixture.componentInstance.onTerm('err');
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('search-error')).toBeTruthy();

    // The outer stream must survive the error — a fresh query still works.
    fixture.componentInstance.onTerm('grace');
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.queryByTestId('search-error')).toBeNull();
    expect(screen.getByTestId('search-results')).toBeTruthy();
    expect(searchFn).toHaveBeenCalledTimes(2);
  });

  it('clears results when the term is shortened back below two characters', async () => {
    const { fixture } = await renderSearch();
    fixture.componentInstance.onTerm('grace');
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.getByTestId('search-results')).toBeTruthy();

    fixture.componentInstance.onTerm('g');
    vi.advanceTimersByTime(300);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(screen.queryByTestId('search-results')).toBeNull();
    expect(screen.getByTestId('search-idle')).toBeTruthy();
  });
});
