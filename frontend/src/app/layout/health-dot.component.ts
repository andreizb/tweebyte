import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { startWith, switchMap, timer } from 'rxjs';
import { GatewayRegistry } from '../core/config/gateway-registry.service';
import { HealthService, HealthState } from '../core/api/services/health.service';

/**
 * Live `/actuator/health` dot for a gateway. Defaults to the active gateway; pass a
 * `gatewayId` to watch a specific one. Backend-agnostic — it only reflects whether the
 * configured URL is reachable, with no notion of what serves it. Pauses when
 * healthPollMs <= 0 (single check only).
 */
@Component({
  selector: 'tb-health-dot',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="inline-block h-2 w-2 rounded-full transition-colors"
      [class.bg-retweet]="state() === 'up'"
      [class.bg-destructive]="state() === 'down'"
      [class.bg-muted-foreground]="state() === 'unknown'"
      [class.animate-pulse-dot]="state() === 'unknown'"
      [attr.title]="title()"
      role="img"
      [attr.aria-label]="title()"
    ></span>
  `
})
export class HealthDotComponent implements OnInit {
  /** Optional gateway id to watch; omit to watch the active gateway. */
  readonly gatewayId = input<string | undefined>(undefined);

  private readonly health = inject(HealthService);
  private readonly registry = inject(GatewayRegistry);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<HealthState>('unknown');

  readonly label = computed(() => {
    const id = this.gatewayId();
    return (id ? this.registry.descriptorFor(id) : this.registry.activeDescriptor())?.label ?? 'Gateway';
  });

  ngOnInit(): void {
    const interval = this.registry.config().healthPollMs;
    if (interval <= 0) {
      this.health
        .check(this.gatewayId())
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((s) => this.state.set(s));
      return;
    }
    timer(0, interval)
      .pipe(
        startWith(0),
        switchMap(() => this.health.check(this.gatewayId())),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((s) => this.state.set(s));
  }

  title(): string {
    const s = this.state();
    const phrase = s === 'up' ? 'healthy' : s === 'down' ? 'unreachable' : 'checking…';
    return `${this.label()}: ${phrase}`;
  }
}
