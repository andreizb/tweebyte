import { Pipe, PipeTransform } from '@angular/core';

/**
 * Matches an ISO datetime (with a `T`/space time part) that carries NO timezone
 * designator — i.e. no trailing `Z` and no `±hh:mm` offset. The backend serialises
 * LocalDateTime with no zone, so such values must be read as UTC, not browser-local.
 */
const TZLESS_ISO_DATETIME = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2})?(\.\d+)?$/;

/**
 * X-style compact relative time: "now", "12s", "5m", "3h", then a date.
 * Backend timestamps are ISO LocalDateTime (no zone) — read as UTC (see normalizeIso).
 */
@Pipe({ name: 'tbRelativeTime', standalone: true, pure: true })
export class RelativeTimePipe implements PipeTransform {
  transform(value: string | number | Date | null | undefined): string {
    if (!value) {
      return '';
    }
    const then =
      typeof value === 'string'
        ? new Date(normalizeIso(value))
        : typeof value === 'number'
          ? new Date(value)
          : value;
    const ms = Date.now() - then.getTime();
    if (Number.isNaN(ms)) {
      return '';
    }
    const sec = Math.floor(ms / 1000);
    if (sec < 5) {
      return 'now';
    }
    if (sec < 60) {
      return `${sec}s`;
    }
    const min = Math.floor(sec / 60);
    if (min < 60) {
      return `${min}m`;
    }
    const hr = Math.floor(min / 60);
    if (hr < 24) {
      return `${hr}h`;
    }
    const day = Math.floor(hr / 24);
    if (day < 7) {
      return `${day}d`;
    }
    const sameYear = then.getFullYear() === new Date().getFullYear();
    return then.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      ...(sameYear ? {} : { year: 'numeric' })
    });
  }
}

/** Append `Z` to a tz-less ISO datetime so it parses as UTC; pass anything else through. */
function normalizeIso(value: string): string {
  return TZLESS_ISO_DATETIME.test(value) ? `${value}Z` : value;
}
