import { ChangeDetectionStrategy, Component, HostBinding, computed, input } from '@angular/core';
import { cn } from '../cn';

export type ButtonVariant =
  | 'default'
  | 'brand'
  | 'secondary'
  | 'ghost'
  | 'outline'
  | 'destructive';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon';

const BASE =
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-full text-sm font-semibold ' +
  'transition-[background,color,transform,opacity] active:scale-[0.97] disabled:pointer-events-none ' +
  'disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ' +
  'focus-visible:ring-offset-2 focus-visible:ring-offset-background select-none';

const VARIANTS: Record<ButtonVariant, string> = {
  default: 'bg-primary text-primary-foreground hover:bg-primary/90',
  brand: 'bg-brand text-brand-foreground hover:bg-brand/90 shadow-sm',
  secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
  ghost: 'hover:bg-accent hover:text-accent-foreground',
  outline: 'border border-border bg-transparent hover:bg-accent hover:text-accent-foreground',
  destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
};

const SIZES: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-10 px-5',
  lg: 'h-12 px-7 text-base',
  icon: 'h-10 w-10 p-0'
};

/**
 * Spartan/shadcn-style button. Use `[variant]`, `[size]`, `[block]`, `[loading]`.
 * Project content as the label. Render as <button tbButton> for native semantics.
 */
@Component({
  selector: 'button[tbButton], a[tbButton]',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <span
        class="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent"
        aria-hidden="true"
      ></span>
    }
    <ng-content />
  `
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('default');
  readonly size = input<ButtonSize>('md');
  readonly block = input(false);
  readonly loading = input(false);

  readonly classes = computed(() =>
    cn(BASE, VARIANTS[this.variant()], SIZES[this.size()], this.block() && 'w-full')
  );

  @HostBinding('class')
  get hostClass(): string {
    return this.classes();
  }

  @HostBinding('attr.data-loading')
  get dataLoading(): string | null {
    return this.loading() ? '' : null;
  }

  @HostBinding('attr.aria-busy')
  get ariaBusy(): string | null {
    return this.loading() ? 'true' : null;
  }
}
