import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ButtonComponent, ButtonSize } from '../button/button.component';
import { FollowService } from '../../../core/api/services/follow.service';
import { ToastService } from '../../../core/state/toast.service';

type FollowUiState = 'idle' | 'following' | 'requested';

/**
 * Optimistic follow/unfollow with follow-request awareness for private accounts.
 * Flips state immediately, calls the API, and rolls back on failure. The owner id
 * (`meId`) is required for the owner-gated follow route.
 */
@Component({
  selector: 'tb-follow-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent],
  template: `
    @if (state() === 'idle') {
      <button tbButton variant="default" [size]="size()" [loading]="busy()" (click)="follow()">
        Follow
      </button>
    } @else if (state() === 'requested') {
      <button tbButton variant="outline" [size]="size()" [loading]="busy()" (click)="cancel()">
        Requested
      </button>
    } @else {
      <button
        tbButton
        variant="outline"
        [size]="size()"
        [loading]="busy()"
        (click)="unfollow()"
        [attr.aria-label]="followingLabel()"
        class="group min-w-[104px] hover:border-destructive/60 hover:text-destructive"
      >
        <span aria-hidden="true" class="group-hover:hidden">Following</span>
        <span aria-hidden="true" class="hidden group-hover:inline">Unfollow</span>
      </button>
    }
  `
})
export class FollowButtonComponent {
  readonly meId = input<string | null>(null);
  readonly targetId = input.required<string>();
  readonly size = input<ButtonSize>('md');
  /** Whether the target is a private account (drives request vs follow). */
  readonly isPrivate = input(false);
  /** Initial follow state, if known by the parent. */
  readonly initial = input<FollowUiState>('idle');

  private readonly follows = inject(FollowService);
  private readonly toast = inject(ToastService);

  readonly state = signal<FollowUiState>('idle');
  readonly busy = signal(false);
  /** Set once the user interacts, so a late-arriving `initial` no longer overrides their action. */
  private readonly touched = signal(false);

  /**
   * Stable accessible name for the following-state button. The visual label swaps
   * "Following" → "Unfollow" on hover (a sighted affordance), so the spans are
   * aria-hidden and this single, hover-independent name drives assistive tech.
   */
  readonly followingLabel = computed(() => 'Following, click to unfollow');

  constructor() {
    // `initial` resolves ASYNCHRONOUSLY — the parent reads the viewer's follow-set over HTTP,
    // so it flips to 'following' after construction. Sync from it reactively (not once), but
    // never clobber a state the user has already toggled.
    effect(
      () => {
        const init = this.initial();
        if (!this.touched()) {
          this.state.set(init);
        }
      },
      { allowSignalWrites: true }
    );
  }

  follow(): void {
    const me = this.meId();
    if (!me || this.busy()) {
      return;
    }
    this.touched.set(true);
    const optimistic: FollowUiState = this.isPrivate() ? 'requested' : 'following';
    this.state.set(optimistic);
    this.busy.set(true);
    this.follows.follow(me, this.targetId()).subscribe({
      next: () => this.busy.set(false),
      error: () => {
        this.state.set('idle');
        this.busy.set(false);
        this.toast.error('Could not follow', 'Please try again.');
      }
    });
  }

  unfollow(): void {
    const me = this.meId();
    if (!me || this.busy()) {
      return;
    }
    this.touched.set(true);
    this.state.set('idle');
    this.busy.set(true);
    this.follows.unfollow(me, this.targetId()).subscribe({
      next: () => this.busy.set(false),
      error: () => {
        this.state.set('following');
        this.busy.set(false);
        this.toast.error('Could not unfollow', 'Please try again.');
      }
    });
  }

  cancel(): void {
    // Cancelling a pending request is an unfollow on the edge.
    this.unfollow();
  }
}
