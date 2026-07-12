import { describe, expect, it } from 'vitest';
import { cn } from './cn';

describe('cn', () => {
  it('joins truthy string fragments', () => {
    expect(cn('a', 'b', 'c')).toBe('a b c');
  });

  it('drops falsy values but keeps 0', () => {
    expect(cn('a', false, null, undefined, '', 'b')).toBe('a b');
    expect(cn('a', 0, 'b')).toBe('a 0 b'); // 0 is kept; b is truthy
  });

  it('flattens nested arrays', () => {
    expect(cn('a', ['b', ['c', false, 'd']])).toBe('a b c d');
  });

  it('stringifies nonzero numbers inside nested arrays', () => {
    expect(cn('grid', [2, ['col', -1]])).toBe('grid 2 col -1');
  });

  it('collapses repeated whitespace and trims', () => {
    expect(cn('  a   b  ', 'c')).toBe('a b c');
  });

  it('preserves duplicate or conflicting classes in caller order', () => {
    expect(cn('px-2', 'px-4', 'px-2')).toBe('px-2 px-4 px-2');
  });

  it('returns an empty string for no/empty input', () => {
    expect(cn()).toBe('');
    expect(cn(null, undefined, false)).toBe('');
  });
});
