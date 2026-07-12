import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { render, screen, fireEvent } from '@testing-library/angular';
import { of, throwError } from 'rxjs';
import { FollowButtonComponent } from './follow-button.component';
import { FollowService } from '../../../core/api/services/follow.service';
import { ToastService } from '../../../core/state/toast.service';

interface MockFollows {
  follow?: ReturnType<typeof vi.fn>;
  unfollow?: ReturnType<typeof vi.fn>;
}

/** Render with a mock FollowService. Inputs default to a valid (meId, targetId). */
async function renderButton(
  follows: MockFollows,
  inputs: Partial<{ meId: string | null; targetId: string; isPrivate: boolean }> = {}
) {
  return render(FollowButtonComponent, {
    componentInputs: {
      meId: 'me',
      targetId: 'target',
      ...inputs
    },
    providers: [{ provide: FollowService, useValue: follows }]
  });
}

/**
 * Put the button into its "following" UI state and render it. A parent-provided `initial`
 * now lands reactively (covered by its own test below); this helper drives the public `state`
 * signal directly as a concise setup for the unfollow-path tests.
 */
function enterFollowing(fixture: {
  componentInstance: FollowButtonComponent;
  detectChanges: () => void;
}) {
  fixture.componentInstance.state.set('following');
  fixture.detectChanges();
}

describe('FollowButtonComponent', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders a "Follow" button by default', async () => {
    await renderButton({ follow: vi.fn().mockReturnValue(of(undefined)) });
    expect(screen.getByRole('button', { name: 'Follow' })).toBeTruthy();
  });

  it('reflects a late-arriving `initial` (parent resolves the follow-set async) — renders "Following"', async () => {
    const { fixture } = await renderButton({ unfollow: vi.fn().mockReturnValue(of(undefined)) });
    // The button first renders "Follow"; the parent then flips [initial] once its async
    // follow-set read resolves. The reactive effect must pick that up (the old one-shot
    // constructor read did not).
    fixture.componentRef.setInput('initial', 'following');
    fixture.detectChanges();
    expect(screen.getByRole('button', { name: /Following/i })).toBeTruthy();
  });

  it('Follow (public): optimistic "Following", calls follow(meId,targetId), stays following on success', async () => {
    const follow = vi.fn().mockReturnValue(of(undefined));
    const { fixture } = await renderButton({ follow }, { isPrivate: false });

    fireEvent.click(screen.getByRole('button', { name: 'Follow' }));

    expect(follow).toHaveBeenCalledWith('me', 'target');
    expect(fixture.componentInstance.state()).toBe('following');
    expect(fixture.componentInstance.busy()).toBe(false); // sync-complete observable
    expect(screen.getByText('Following')).toBeTruthy();
  });

  it('Follow (private): optimistic "Requested"', async () => {
    const follow = vi.fn().mockReturnValue(of(undefined));
    const { fixture } = await renderButton({ follow }, { isPrivate: true });

    fireEvent.click(screen.getByRole('button', { name: 'Follow' }));

    expect(follow).toHaveBeenCalledWith('me', 'target');
    expect(fixture.componentInstance.state()).toBe('requested');
    expect(screen.getByRole('button', { name: 'Requested' })).toBeTruthy();
  });

  it('Follow error: rolls back to idle and shows an error toast', async () => {
    const follow = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    const { fixture } = await renderButton({ follow });
    const error = vi.spyOn(TestBed.inject(ToastService), 'error');

    fireEvent.click(screen.getByRole('button', { name: 'Follow' }));

    expect(fixture.componentInstance.state()).toBe('idle');
    expect(fixture.componentInstance.busy()).toBe(false);
    expect(error).toHaveBeenCalledWith('Could not follow', 'Please try again.');
    expect(screen.getByRole('button', { name: 'Follow' })).toBeTruthy();
  });

  it('Unfollow from following: calls unfollow(meId,targetId) and returns to idle', async () => {
    const unfollow = vi.fn().mockReturnValue(of(undefined));
    const { fixture } = await renderButton({ unfollow });

    enterFollowing(fixture);
    expect(screen.getByText('Following')).toBeTruthy();

    // The following-state button carries both "Following" and "Unfollow" hover spans.
    fireEvent.click(screen.getByText('Unfollow'));

    expect(unfollow).toHaveBeenCalledWith('me', 'target');
    expect(fixture.componentInstance.state()).toBe('idle');
    expect(screen.getByRole('button', { name: 'Follow' })).toBeTruthy();
  });

  it('Unfollow error: rolls back to following and shows an error toast', async () => {
    const unfollow = vi.fn().mockReturnValue(throwError(() => new Error('x')));
    const { fixture } = await renderButton({ unfollow });
    const error = vi.spyOn(TestBed.inject(ToastService), 'error');

    enterFollowing(fixture);
    fireEvent.click(screen.getByText('Unfollow'));

    expect(unfollow).toHaveBeenCalledWith('me', 'target');
    expect(fixture.componentInstance.state()).toBe('following');
    expect(error).toHaveBeenCalledWith('Could not unfollow', 'Please try again.');
  });

  it('follow() is a no-op when meId is null', async () => {
    const follow = vi.fn().mockReturnValue(of(undefined));
    const { fixture } = await renderButton({ follow }, { meId: null });

    fireEvent.click(screen.getByRole('button', { name: 'Follow' }));

    expect(follow).not.toHaveBeenCalled();
    expect(fixture.componentInstance.state()).toBe('idle');
  });

  it('unfollow() is a no-op when meId is null', async () => {
    const unfollow = vi.fn().mockReturnValue(of(undefined));
    const { fixture } = await renderButton({ unfollow }, { meId: null });

    enterFollowing(fixture);
    fireEvent.click(screen.getByText('Unfollow'));

    expect(unfollow).not.toHaveBeenCalled();
    expect(fixture.componentInstance.state()).toBe('following'); // unchanged
  });
});
