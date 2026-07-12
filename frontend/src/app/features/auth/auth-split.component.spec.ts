import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/angular';
import { Component } from '@angular/core';
import { AuthSplitComponent } from './auth-split.component';

@Component({
  standalone: true,
  imports: [AuthSplitComponent],
  template: `<tb-auth-split><p>PROJECTED</p></tb-auth-split>`
})
class HostComponent {}

describe('AuthSplitComponent', () => {
  it('renders the branded hero copy', async () => {
    await render(HostComponent);
    const text = document.body.textContent ?? '';
    expect(text).toContain('Happening now.');
    expect(text).toContain('Join');
    expect(text).toContain('Tweebyte');
    expect(text).toContain('today.');
  });

  it('projects the slotted form content via <ng-content>', async () => {
    await render(HostComponent);
    expect(document.body.textContent).toContain('PROJECTED');
  });

  it('stays backend-agnostic: the rendered copy names no implementation', async () => {
    await render(HostComponent);
    // NEUTRALITY: nothing in the hero copy may hint at how the app is served.
    expect(document.body.textContent).not.toMatch(/async|reactive|webflux|mvc|blocking|event-loop/i);
  });
});
