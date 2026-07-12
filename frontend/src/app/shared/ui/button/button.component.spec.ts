import { describe, expect, it } from 'vitest';
import { Component } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import { ButtonComponent } from './button.component';

@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `<button tbButton [variant]="variant" [size]="size" [block]="block" [loading]="loading">Go</button>`
})
class HostComponent {
  variant: 'default' | 'brand' | 'outline' = 'brand';
  size: 'sm' | 'md' | 'lg' | 'icon' = 'md';
  block = false;
  loading = false;
}

describe('ButtonComponent', () => {
  it('projects its label and applies the variant + size classes', async () => {
    await render(HostComponent);
    const btn = screen.getByRole('button', { name: 'Go' });
    expect(btn.className).toContain('bg-brand');
    expect(btn.className).toContain('h-10'); // md
  });

  it('adds w-full when block is set', async () => {
    await render(HostComponent, { componentProperties: { block: true } });
    expect(screen.getByRole('button').className).toContain('w-full');
  });

  it('exposes a busy spinner + aria-busy when loading', async () => {
    await render(HostComponent, { componentProperties: { loading: true } });
    const btn = screen.getByRole('button');
    expect(btn.getAttribute('aria-busy')).toBe('true');
    expect(btn.querySelector('.animate-spin')).not.toBeNull();
  });

  it('switches classes when variant + size change', async () => {
    await render(HostComponent, {
      componentProperties: { variant: 'outline', size: 'lg' }
    });
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('border');
    expect(btn.className).toContain('h-12'); // lg
  });
});
