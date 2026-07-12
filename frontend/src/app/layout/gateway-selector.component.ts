import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { GatewayRegistry } from '../core/config/gateway-registry.service';
import { LatencyService } from '../core/state/latency.service';
import { ToastService } from '../core/state/toast.service';
import { HealthDotComponent } from './health-dot.component';

/**
 * Neutral gateway selector — a thin, backend-agnostic environment/URL chooser.
 *
 * The frontend talks to ONE gateway base URL and neither knows nor cares what serves it.
 * When a single gateway is configured this just surfaces a health dot + round-trip badge
 * for the active URL. When the operator configures several interchangeable gateways, the
 * labelled buttons switch the active base URL for the NEXT request (no reload; the bearer
 * survives, since the gateways are interchangeable by contract). No implementation is
 * named or styled differently — the choice is purely "which URL".
 */
@Component({
  selector: 'tb-gateway-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [HealthDotComponent],
  template: `
    <div class="flex flex-col gap-2 rounded-2xl border border-border bg-secondary/40 p-3">
      <div class="flex items-center justify-between">
        <span class="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          Connection
        </span>
        @if (showLatency()) {
          <span class="font-mono text-[11px] text-muted-foreground" title="Last request round-trip">
            {{ latencyLabel() }}
          </span>
        }
      </div>

      @if (registry.hasMultiple()) {
        <div
          role="radiogroup"
          aria-label="Active gateway"
          class="grid gap-1 rounded-full bg-background p-1"
          [style.gridTemplateColumns]="'repeat(' + registry.gateways().length + ', minmax(0, 1fr))'"
        >
          @for (gw of registry.gateways(); track gw.id) {
            <button
              type="button"
              role="radio"
              [attr.aria-checked]="registry.activeId() === gw.id"
              (click)="select(gw.id)"
              class="relative z-10 flex items-center justify-center gap-2 rounded-full px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              [class.bg-brand]="registry.activeId() === gw.id"
              [class.text-brand-foreground]="registry.activeId() === gw.id"
              [class.text-muted-foreground]="registry.activeId() !== gw.id"
            >
              <tb-health-dot [gatewayId]="gw.id" />
              <span class="truncate">{{ gw.label }}</span>
            </button>
          }
        </div>
      } @else {
        <div class="flex items-center gap-2 px-1 py-1 text-sm">
          <tb-health-dot />
          <span class="font-semibold">{{ registry.activeDescriptor().label }}</span>
        </div>
      }

      <p class="truncate px-1 font-mono text-[11px] leading-snug text-muted-foreground" [attr.title]="registry.activeBaseUrl()">
        {{ registry.activeBaseUrl() }}
      </p>
    </div>
  `
})
export class GatewaySelectorComponent {
  readonly registry = inject(GatewayRegistry);
  private readonly latency = inject(LatencyService);
  private readonly toast = inject(ToastService);

  readonly showLatency = computed(() => this.registry.config().showLatency);

  readonly latencyLabel = computed(() => {
    const ms = this.latency.lastMs();
    return ms === null ? '—' : `${ms} ms`;
  });

  select(id: string): void {
    if (id === this.registry.activeId()) {
      return;
    }
    this.registry.setActive(id);
    const d = this.registry.activeDescriptor();
    this.toast.info(`Connected to ${d.label}`, `Next request → ${d.baseUrl} · session preserved`);
  }
}
