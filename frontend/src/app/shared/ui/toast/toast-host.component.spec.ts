import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { TestBed } from '@angular/core/testing';
import { ToastHostComponent } from './toast-host.component';
import { ToastService } from '../../../core/state/toast.service';

describe('ToastHostComponent', () => {
  it('renders a toast message and detail pushed via the real ToastService', async () => {
    const { fixture } = await render(ToastHostComponent);
    const toast = TestBed.inject(ToastService);

    toast.success('Saved', 'detail');
    fixture.detectChanges();

    expect(screen.getByText('Saved')).toBeTruthy();
    expect(screen.getByText('detail')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Dismiss notification' })).toBeTruthy();
  });

  it('removes the toast when the dismiss button is clicked', async () => {
    const { fixture } = await render(ToastHostComponent);
    const toast = TestBed.inject(ToastService);

    toast.success('Saved', 'detail');
    fixture.detectChanges();

    const dismiss = screen.getByRole('button', { name: 'Dismiss notification' });
    dismiss.click();
    fixture.detectChanges();

    expect(screen.queryByText('Saved')).toBeNull();
  });

  it('exposes an aria-live="polite" region', async () => {
    const { container } = await render(ToastHostComponent);
    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull();
  });

  it('kindClass maps kinds (error -> contains "border-destructive")', async () => {
    const { fixture } = await render(ToastHostComponent);
    expect(fixture.componentInstance.kindClass('error')).toContain('border-destructive');
  });

  it('dotClass maps kinds (success -> contains "bg-retweet")', async () => {
    const { fixture } = await render(ToastHostComponent);
    expect(fixture.componentInstance.dotClass('success')).toContain('bg-retweet');
  });
});
