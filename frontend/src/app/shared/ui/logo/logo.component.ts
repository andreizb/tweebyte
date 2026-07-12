import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Tweebyte wordmark + glyph. Single source of truth for branding — when a final logo
 * lands in design-references/, swap the SVG here and nowhere else.
 *
 * The glyph is a triple-chevron (»») evoking a fast byte/token stream, in brand blue.
 */
@Component({
  selector: 'tb-logo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="inline-flex items-center gap-2" [class.text-2xl]="size() === 'lg'">
      <svg
        [attr.width]="glyphPx()"
        [attr.height]="glyphPx()"
        viewBox="0 0 32 32"
        fill="none"
        aria-hidden="true"
        class="shrink-0"
      >
        <path
          d="M9 8 L15 16 L9 24 M14 8 L20 16 L14 24 M19 8 L25 16 L19 24"
          stroke="hsl(var(--brand))"
          stroke-width="2.4"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      @if (showWordmark()) {
        <span class="font-bold tracking-tight">
          <span class="text-foreground">Twee</span><span class="text-brand">byte</span>
        </span>
      }
    </span>
  `
})
export class LogoComponent {
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly showWordmark = input(true);

  glyphPx(): number {
    return this.size() === 'lg' ? 34 : this.size() === 'sm' ? 24 : 28;
  }
}
