import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MediaService } from '../../../core/api/services/media.service';

const SIZE_PX: Record<string, string> = {
  sm: 'h-9 w-9 text-sm',
  md: 'h-11 w-11 text-base',
  lg: 'h-20 w-20 text-2xl',
  xl: 'h-32 w-32 text-4xl'
};

/**
 * Avatar with a bearer-aware blob image. GET /media/{id} needs the Authorization header
 * (an <img src> can't send it), so MediaService fetches the bytes and we bind the object
 * URL. Falls back to a tinted monogram while loading or when there is no picture.
 */
@Component({
  selector: 'tb-avatar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      role="img"
      class="inline-grid shrink-0 place-items-center overflow-hidden rounded-full bg-secondary font-bold uppercase text-foreground/80"
      [class]="sizeClass()"
      [attr.aria-label]="label()"
    >
      @if (url()) {
        <img [src]="url()!" alt="" class="h-full w-full object-cover" loading="lazy" />
      } @else {
        <span aria-hidden="true">{{ monogram() }}</span>
      }
    </span>
  `
})
export class AvatarComponent {
  readonly mediaId = input<string | null | undefined>(null);
  readonly name = input<string | null | undefined>(null);
  readonly size = input<'sm' | 'md' | 'lg' | 'xl'>('md');

  private readonly media = inject(MediaService);
  private readonly destroyRef = inject(DestroyRef);

  /** Media id currently held via media.acquire(); released on change/destroy so the URL is revoked. */
  private heldId: string | null = null;

  readonly url = signal<string | null>(null);
  readonly sizeClass = computed(() => SIZE_PX[this.size()]);
  readonly label = computed(() => (this.name() ? `${this.name()} avatar` : 'avatar'));

  constructor() {
    effect(
      () => {
        const id = this.mediaId() ?? null;
        this.releaseHeld();
        this.url.set(null);
        if (!id) {
          return;
        }
        this.heldId = id;
        this.media
          .acquire(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: (u) => this.url.set(u),
            error: () => this.url.set(null)
          });
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

  monogram(): string {
    return (this.name() ?? '?').charAt(0).toUpperCase();
  }
}
