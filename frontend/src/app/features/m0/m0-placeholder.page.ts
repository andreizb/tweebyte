import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'tb-m0-placeholder',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-h-screen' },
  template: `
    <section class="border-b border-border px-4 py-4">
      <h1 class="text-xl font-bold">Tweebyte</h1>
      <p class="mt-1 text-sm text-muted-foreground">Frontend contracts and backend plumbing are wired.</p>
    </section>
  `
})
export class M0PlaceholderPage {}
