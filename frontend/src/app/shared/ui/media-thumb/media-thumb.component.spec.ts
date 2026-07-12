import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/angular';
import { of } from 'rxjs';
import { MediaThumbComponent } from './media-thumb.component';
import { MediaService } from '../../../core/api/services/media.service';

function mediaMock() {
  return { acquire: vi.fn().mockReturnValue(of('blob:fake')), release: vi.fn() };
}

describe('MediaThumbComponent', () => {
  it('calls media.acquire(mediaId)', async () => {
    const media = mediaMock();
    await render(MediaThumbComponent, {
      componentInputs: { mediaId: 'media-7' },
      providers: [{ provide: MediaService, useValue: media }]
    });

    expect(media.acquire).toHaveBeenCalledWith('media-7');
  });

  it('shows the <img> with the resolved url once acquire emits', async () => {
    const { fixture, container } = await render(MediaThumbComponent, {
      componentInputs: { mediaId: 'media-7' },
      providers: [{ provide: MediaService, useValue: mediaMock() }]
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const img = container.querySelector('img');
    expect(img).not.toBeNull();
    expect(img!.getAttribute('src')).toContain('blob:fake');
    expect(container.querySelector('.skeleton')).toBeNull();
  });

  it('renders a skeleton while the url is unresolved', async () => {
    const media = { acquire: vi.fn().mockReturnValue(of()), release: vi.fn() };
    const { container } = await render(MediaThumbComponent, {
      componentInputs: { mediaId: 'media-7' },
      providers: [{ provide: MediaService, useValue: media }]
    });

    expect(container.querySelector('.skeleton')).not.toBeNull();
    expect(container.querySelector('img')).toBeNull();
  });

  it('releases the held media id when it changes and on destroy (no blob leak)', async () => {
    const media = mediaMock();
    const { fixture } = await render(MediaThumbComponent, {
      componentInputs: { mediaId: 'media-7' },
      providers: [{ provide: MediaService, useValue: media }]
    });
    fixture.detectChanges();

    fixture.componentRef.setInput('mediaId', 'media-8');
    fixture.detectChanges();
    expect(media.release).toHaveBeenCalledWith('media-7');
    expect(media.acquire).toHaveBeenCalledWith('media-8');

    fixture.destroy();
    expect(media.release).toHaveBeenCalledWith('media-8');
  });
});
