import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { TweetDto } from '../../core/api/models/tweet.model';
import { ComposeBoxComponent } from '../../shared/ui/compose-box/compose-box.component';

/**
 * Standalone compose route (the nav-rail "Post" CTA). Reuses the ComposeBox; on a
 * successful post it returns to the home timeline.
 */
@Component({
  selector: 'tb-compose-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ComposeBoxComponent],
  host: { class: 'block' },
  template: `
    <header class="flex items-center gap-4 border-b border-border px-4 py-3">
      <button
        type="button"
        class="grid h-9 w-9 place-items-center rounded-full hover:bg-accent"
        (click)="back()"
        aria-label="Back"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <h1 class="text-xl font-bold">New post</h1>
    </header>

    <tb-compose-box placeholder="What is happening?!" (posted)="onPosted($event)" />
  `
})
export class ComposePage {
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  onPosted(_tweet: TweetDto): void {
    void this.router.navigate(['/home']);
  }

  back(): void {
    this.location.back();
  }
}
