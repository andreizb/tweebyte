import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { M0PlaceholderPage } from './m0-placeholder.page';

describe('M0PlaceholderPage', () => {
  it('renders the placeholder heading (used by not-yet-built routes)', async () => {
    await render(M0PlaceholderPage);
    expect(screen.getByText('Tweebyte')).toBeTruthy();
  });
});
