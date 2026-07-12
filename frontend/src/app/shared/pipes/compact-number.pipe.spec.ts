import { describe, expect, it } from 'vitest';
import { CompactNumberPipe } from './compact-number.pipe';

describe('CompactNumberPipe', () => {
  const pipe = new CompactNumberPipe();

  it('hides zero by default', () => {
    expect(pipe.transform(0)).toBe('');
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });

  it('renders zero when hideZero is false', () => {
    expect(pipe.transform(0, false)).toBe('0');
  });

  it('renders nullish values as zero when hideZero is false', () => {
    expect(pipe.transform(null, false)).toBe('0');
    expect(pipe.transform(undefined, false)).toBe('0');
  });

  it('passes through values under 1000', () => {
    expect(pipe.transform(1)).toBe('1');
    expect(pipe.transform(942)).toBe('942');
    expect(pipe.transform(999)).toBe('999');
  });

  it('compacts thousands with one decimal', () => {
    expect(pipe.transform(1000)).toBe('1K');
    expect(pipe.transform(12300)).toBe('12.3K');
    expect(pipe.transform(999_999)).toBe('1000K');
  });

  it('rounds thousands to one decimal and trims a trailing .0', () => {
    expect(pipe.transform(12_000)).toBe('12K');
    expect(pipe.transform(12_360)).toBe('12.4K');
  });

  it('compacts millions with one decimal', () => {
    expect(pipe.transform(1_000_000)).toBe('1M');
    expect(pipe.transform(2_400_000)).toBe('2.4M');
  });

  it('rounds million-scale values to one decimal and trims a trailing .0', () => {
    expect(pipe.transform(1_250_000)).toBe('1.3M');
    expect(pipe.transform(10_000_000)).toBe('10M');
  });
});
