import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/api/services/auth.service';
import { SessionStore } from '../../core/state/session.store';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { LogoComponent } from '../../shared/ui/logo/logo.component';
import { AuthSplitComponent } from './auth-split.component';

/**
 * M2 login. ROPC: POST {email,password} -> {token}; decode user_id+exp; store bearer.
 * On success, return to the requested URL (or /home). 401 surfaces inline.
 */
@Component({
  selector: 'tb-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, LogoComponent, AuthSplitComponent],
  template: `
    <tb-auth-split>
      <form [formGroup]="form" (ngSubmit)="submit()" class="flex w-full max-w-sm flex-col gap-5" novalidate>
        <div class="flex flex-col gap-2">
          <tb-logo size="lg" />
          <h1 class="mt-4 text-3xl font-bold tracking-tight">Sign in to Tweebyte</h1>
          <p class="text-sm text-muted-foreground">Welcome back. See what's happening.</p>
        </div>

        @if (error()) {
          <div class="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
            {{ error() }}
          </div>
        }

        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">Email</span>
          <input
            type="email"
            formControlName="email"
            autocomplete="email"
            class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none transition focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
            placeholder="ada@tweebyte.dev"
          />
        </label>

        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">Password</span>
          <input
            type="password"
            formControlName="password"
            autocomplete="current-password"
            class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none transition focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
            placeholder="••••••••"
          />
        </label>

        <button tbButton variant="brand" size="lg" type="submit" [block]="true" [loading]="loading()" [disabled]="form.invalid || loading()">
          Sign in
        </button>

        <p class="text-sm text-muted-foreground">
          New here?
          <a routerLink="/register" class="font-semibold text-brand hover:underline">Create an account</a>
        </p>
      </form>
    </tb-auth-split>
  `
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    this.session.setAuthenticating(true);

    this.auth
      .login(this.form.getRawValue())
      .pipe(
        finalize(() => {
          this.loading.set(false);
          this.session.setAuthenticating(false);
        })
      )
      .subscribe({
        next: (res) => {
          if (this.session.setToken(res.token)) {
            const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/home';
            void this.router.navigateByUrl(returnUrl);
          } else {
            this.error.set('Received an invalid token. Please try again.');
          }
        },
        error: () => this.error.set('Wrong email or password.')
      });
  }
}
