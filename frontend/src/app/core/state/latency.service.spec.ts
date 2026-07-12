import { describe, expect, it } from 'vitest';
import { LatencyService } from './latency.service';

describe('LatencyService', () => {
  it('starts null, records a rounded ms, and resets', () => {
    const svc = new LatencyService();
    expect(svc.lastMs()).toBeNull();
    svc.record(12.7);
    expect(svc.lastMs()).toBe(13);
    svc.reset();
    expect(svc.lastMs()).toBeNull();
  });
});
