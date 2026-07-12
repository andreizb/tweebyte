import { describe, expect, it, vi, afterEach } from 'vitest';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  afterEach(() => { vi.useRealTimers(); });

  it('pushes each kind with a unique id and message/detail', () => {
    const svc = new ToastService();
    svc.info('i', 'id');
    svc.success('s');
    svc.warn('w');
    svc.error('e', 'ed');
    const list = svc.toasts();
    expect(list.map((t) => t.kind)).toEqual(['info', 'success', 'warn', 'error']);
    expect(list[0].detail).toBe('id');
    expect(new Set(list.map((t) => t.id)).size).toBe(4);
  });

  it('dismiss removes a toast by id', () => {
    const svc = new ToastService();
    svc.info('a');
    const id = svc.toasts()[0].id;
    svc.dismiss(id);
    expect(svc.toasts()).toHaveLength(0);
  });

  it('auto-dismisses after the TTL', () => {
    vi.useFakeTimers();
    const svc = new ToastService();
    svc.info('a');
    expect(svc.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(4500);
    expect(svc.toasts()).toHaveLength(0);
  });

  it('auto-dismisses each toast on its own timer', () => {
    vi.useFakeTimers();
    const svc = new ToastService();
    svc.info('first');
    vi.advanceTimersByTime(1000);
    svc.info('second');
    vi.advanceTimersByTime(3500);
    expect(svc.toasts().map((t) => t.message)).toEqual(['second']);
    vi.advanceTimersByTime(1000);
    expect(svc.toasts()).toHaveLength(0);
  });

  it('keeps a manually dismissed toast gone when its timer later fires', () => {
    vi.useFakeTimers();
    const svc = new ToastService();
    svc.info('manual');
    svc.dismiss(svc.toasts()[0].id);
    vi.advanceTimersByTime(4500);
    expect(svc.toasts()).toHaveLength(0);
  });

  it('errors get a longer TTL than info', () => {
    vi.useFakeTimers();
    const svc = new ToastService();
    svc.error('boom');
    vi.advanceTimersByTime(4500);
    expect(svc.toasts()).toHaveLength(1); // still up
    vi.advanceTimersByTime(1500);
    expect(svc.toasts()).toHaveLength(0);
  });
});
