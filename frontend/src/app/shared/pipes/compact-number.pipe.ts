import { Pipe, PipeTransform } from '@angular/core';

/** X-style compact counts: 0 -> '', 942 -> '942', 12300 -> '12.3K', 2_400_000 -> '2.4M'. */
@Pipe({ name: 'tbCompactNumber', standalone: true, pure: true })
export class CompactNumberPipe implements PipeTransform {
  transform(value: number | null | undefined, hideZero = true): string {
    const n = value ?? 0;
    if (n === 0 && hideZero) {
      return '';
    }
    if (n < 1000) {
      return String(n);
    }
    if (n < 1_000_000) {
      return `${trim(n / 1000)}K`;
    }
    return `${trim(n / 1_000_000)}M`;
  }
}

function trim(x: number): string {
  return (Math.round(x * 10) / 10).toString();
}
