import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './shared/ui/toast/toast-host.component';

@Component({
  selector: 'tb-root',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ToastHostComponent],
  template: `
    <router-outlet />
    <tb-toast-host />
  `
})
export class AppComponent {}
