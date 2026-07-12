import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MediaService } from '../../../core/api/services/media.service';

/**
 * Bearer-aware media tile for tweet attachments. Same blob strategy as Avatar
 * (GET /media/{id} needs the Authorization header). Shows a shimmer until loaded.
 */
@Component({
  selector: 'tb-media-thumb',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (url()) {
      <img [src]="url()!" alt="Attached media" class="h-full max-h-[510px] w-full object-cover" loading="lazy" />
    } @else {
      <div class="skeleton aspect-video w-full"></div>
    }
  `
})
export class MediaThumbComponent {
  readonly mediaId = input.required<string>();

  private readonly media = inject(MediaService);
  private readonly destroyRef = inject(DestroyRef);
  readonly url = signal<string | null>(null);

  /** Media id currently held via media.acquire(); released on change/destroy so the URL is revoked. */
  private heldId: string | null = null;

  constructor() {
    effect(
      () => {
        const id = this.mediaId();
        this.releaseHeld();
        this.url.set(null);
        if (!id) {
          return;
        }
        this.heldId = id;
        this.media
          .acquire(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({ next: (u) => this.url.set(u), error: () => this.url.set(null) });
      },
      { allowSignalWrites: true }
    );
    this.destroyRef.onDestroy(() => this.releaseHeld());
  }

  private releaseHeld(): void {
    if (this.heldId) {
      this.media.release(this.heldId);
      this.heldId = null;
    }
  }
}
