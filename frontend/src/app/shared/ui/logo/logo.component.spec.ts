import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { LogoComponent } from './logo.component';

describe('LogoComponent', () => {
  it('renders the SVG glyph', async () => {
    const { container } = await render(LogoComponent);
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg!.querySelector('path')).not.toBeNull();
  });

  it('shows the "Twee"/"byte" wordmark by default', async () => {
    await render(LogoComponent);
    expect(screen.getByText('Twee')).toBeTruthy();
    expect(screen.getByText('byte')).toBeTruthy();
  });

  it('hides the wordmark when showWordmark is false', async () => {
    await render(LogoComponent, { componentInputs: { showWordmark: false } });
    expect(screen.queryByText('Twee')).toBeNull();
    expect(screen.queryByText('byte')).toBeNull();
  });

  it('glyphPx() returns 34 for size "lg"', async () => {
    const { fixture } = await render(LogoComponent, { componentInputs: { size: 'lg' } });
    expect(fixture.componentInstance.glyphPx()).toBe(34);
  });

  it('glyphPx() returns 24 for size "sm"', async () => {
    const { fixture } = await render(LogoComponent, { componentInputs: { size: 'sm' } });
    expect(fixture.componentInstance.glyphPx()).toBe(24);
  });

  it('glyphPx() returns 28 for the default size', async () => {
    const { fixture } = await render(LogoComponent);
    expect(fixture.componentInstance.glyphPx()).toBe(28);
  });
});
