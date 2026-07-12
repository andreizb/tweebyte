import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/angular';
import { of } from 'rxjs';
import { AvatarComponent } from './avatar.component';
import { MediaService } from '../../../core/api/services/media.service';

function mediaMock() {
  return { acquire: vi.fn().mockReturnValue(of('blob:fake')), release: vi.fn() };
}

describe('AvatarComponent', () => {
  it('renders the monogram (first letter, uppercased) when there is no mediaId', async () => {
    const media = mediaMock();
    const { container } = await render(AvatarComponent, {
      componentInputs: { name: 'andrei' },
      providers: [{ provide: MediaService, useValue: media }]
    });

    expect(container.textContent).toContain('A');
    expect(container.querySelector('img')).toBeNull();
    expect(media.acquire).not.toHaveBeenCalled();
  });

  it('monogram() returns "?" when there is no name', async () => {
    const { fixture } = await render(AvatarComponent, {
      providers: [{ provide: MediaService, useValue: mediaMock() }]
    });
    expect(fixture.componentInstance.monogram()).toBe('?');
  });

  it('sizeClass() maps the size (lg -> contains "h-20")', async () => {
    const { fixture } = await render(AvatarComponent, {
      componentInputs: { size: 'lg' },
      providers: [{ provide: MediaService, useValue: mediaMock() }]
    });
    expect(fixture.componentInstance.sizeClass()).toContain('h-20');
  });

  it('acquires media.acquire(id) and binds the returned url to an <img> when a mediaId is set', async () => {
    const media = mediaMock();
    const { fixture } = await render(AvatarComponent, {
      componentInputs: { mediaId: 'media-1', name: 'andrei' },
      providers: [{ provide: MediaService, useValue: media }]
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(media.acquire).toHaveBeenCalledWith('media-1');

    const img = document.querySelector('img');
    expect(img).not.toBeNull();
    expect(img!.getAttribute('src')).toContain('blob:fake');
  });

  it('releases the held media id when it changes and on destroy (no blob leak)', async () => {
    const media = mediaMock();
    const { fixture } = await render(AvatarComponent, {
      componentInputs: { mediaId: 'media-1' },
      providers: [{ provide: MediaService, useValue: media }]
    });
    fixture.detectChanges();

    fixture.componentRef.setInput('mediaId', 'media-2');
    fixture.detectChanges();
    expect(media.release).toHaveBeenCalledWith('media-1');
    expect(media.acquire).toHaveBeenCalledWith('media-2');

    fixture.destroy();
    expect(media.release).toHaveBeenCalledWith('media-2');
  });
});
