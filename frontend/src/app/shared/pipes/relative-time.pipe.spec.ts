import { describe, expect, it, vi, afterEach } from 'vitest';
import { RelativeTimePipe } from './relative-time.pipe';

describe('RelativeTimePipe', () => {
  const pipe = new RelativeTimePipe();
  const NOW = new Date('2026-06-20T12:00:00').getTime();

  afterEach(() => { vi.useRealTimers(); });

  function at(deltaMs: number): string {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    return pipe.transform(new Date(NOW - deltaMs));
  }

  it('returns empty for null/undefined/invalid', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('not-a-date')).toBe('');
  });

  it('says "now" under 5 seconds', () => {
    expect(at(2_000)).toBe('now');
  });

  it('switches from now to seconds at the five-second boundary', () => {
    expect(at(5_000)).toBe('5s');
  });

  it('renders seconds, minutes, hours, days', () => {
    expect(at(30_000)).toBe('30s');
    expect(at(5 * 60_000)).toBe('5m');
    expect(at(3 * 3_600_000)).toBe('3h');
    expect(at(2 * 86_400_000)).toBe('2d');
  });

  it('uses the next unit at exact minute, hour, and day boundaries', () => {
    expect(at(60_000)).toBe('1m');
    expect(at(3_600_000)).toBe('1h');
    expect(at(86_400_000)).toBe('1d');
  });

  it('treats future timestamps as now', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    expect(pipe.transform(new Date(NOW + 60_000))).toBe('now');
  });

  it('falls back to a localized date beyond a week', () => {
    const out = at(10 * 86_400_000);
    expect(out).not.toMatch(/now|\d+[smhd]$/);
    expect(out.length).toBeGreaterThan(0);
  });

  it('accepts ISO strings and epoch numbers', () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
    expect(pipe.transform(new Date(NOW - 60_000).toISOString())).toBe('1m');
    expect(pipe.transform(NOW - 60_000)).toBe('1m');
  });

  // A9: backend LocalDateTime carries no zone — read it as UTC, not browser-local, so the
  // delta is the same on any machine. These assertions are timezone-independent by design;
  // deltas sit mid-bucket to stay clear of the boundary rounding.
  it('treats a tz-less ISO timestamp as UTC', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-20T12:00:30Z').getTime());
    // 90s earlier in UTC, written WITHOUT a zone designator (the backend shape) => "1m".
    expect(pipe.transform('2026-06-20T11:59:00')).toBe('1m');
    // sub-second precision variant is still treated as UTC.
    expect(pipe.transform('2026-06-20T11:59:00.123')).toBe('1m');
  });

  it('treats a tz-less ISO timestamp with a space separator as UTC', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-20T12:00:30Z').getTime());
    expect(pipe.transform('2026-06-20 11:59:00')).toBe('1m');
  });

  it('a tz-less and an explicit-UTC timestamp for the same instant agree', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-20T12:00:30Z').getTime());
    expect(pipe.transform('2026-06-20T11:57:00')).toBe(pipe.transform('2026-06-20T11:57:00Z'));
  });

  it('does not re-apply UTC to a timestamp that already carries a zone', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-20T12:00:30Z').getTime());
    // +02:00 offset => the instant is 09:58Z, i.e. ~2h ago, NOT ~2m ago.
    expect(pipe.transform('2026-06-20T11:58:00+02:00')).toBe('2h');
  });
});
