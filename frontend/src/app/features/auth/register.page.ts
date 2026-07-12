import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of, switchMap, EMPTY, Observable } from 'rxjs';
import { AuthService } from '../../core/api/services/auth.service';
import { MediaService } from '../../core/api/services/media.service';
import { UserService } from '../../core/api/services/user.service';
import { SessionStore } from '../../core/state/session.store';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { LogoComponent } from '../../shared/ui/logo/logo.component';
import { AuthSplitComponent } from './auth-split.component';

/**
 * M2 register. The gateway only lets /auth/register through unauthenticated, so an avatar
 * cannot be uploaded before a token exists. Order: register -> {token} (multipart
 * UserRegisterRequest), store the session, THEN — if an avatar was chosen — upload it via
 * POST /media (now bearer-authed) and attach it with PUT /users/{id}. A failed avatar never
 * blocks the already-created account: we land home regardless.
 */
@Component({
  selector: 'tb-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, LogoComponent, AuthSplitComponent],
  template: `
    <tb-auth-split>
      <form [formGroup]="form" (ngSubmit)="submit()" class="flex w-full max-w-sm flex-col gap-4" novalidate>
        <div class="flex flex-col gap-1">
          <tb-logo size="lg" />
          <h1 class="mt-3 text-3xl font-bold tracking-tight">Create your account</h1>
        </div>

        @if (error()) {
          <div class="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
            {{ error() }}
          </div>
        }

        <div class="flex items-center gap-3">
          <label class="grid h-16 w-16 shrink-0 cursor-pointer place-items-center overflow-hidden rounded-full border border-dashed border-input bg-secondary text-xs text-muted-foreground hover:border-ring">
            @if (avatarPreview()) {
              <img [src]="avatarPreview()!" alt="Avatar preview" class="h-full w-full object-cover" />
            } @else {
              <span>Avatar</span>
            }
            <input type="file" accept="image/*" class="hidden" (change)="onAvatar($event)" />
          </label>
          <p class="text-xs text-muted-foreground">Optional — added to your profile right after sign-up.</p>
        </div>

        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">Username</span>
          <input formControlName="userName" autocomplete="username" class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40" placeholder="ada" />
        </label>

        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">Email</span>
          <input type="email" formControlName="email" autocomplete="email" class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40" placeholder="ada@tweebyte.dev" />
        </label>

        <label class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">Password</span>
          <input type="password" formControlName="password" autocomplete="new-password" class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40" placeholder="At least 8 characters" />
        </label>

        <div class="flex gap-3">
          <label class="flex flex-1 flex-col gap-1.5">
            <span class="text-sm font-medium">Birth date</span>
            <input type="date" formControlName="birthDate" class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40" />
          </label>
          <label class="mt-7 flex items-center gap-2 text-sm">
            <input type="checkbox" formControlName="isPrivate" class="h-4 w-4 rounded border-input accent-brand" />
            Private
          </label>
        </div>

        <button tbButton variant="brand" size="lg" type="submit" [block]="true" [loading]="loading()" [disabled]="form.invalid || loading()">
          Create account
        </button>

        <p class="text-sm text-muted-foreground">
          Already have an account?
          <a routerLink="/login" class="font-semibold text-brand hover:underline">Sign in</a>
        </p>
      </form>
    </tb-auth-split>
  `
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly media = inject(MediaService);
  private readonly users = inject(UserService);
  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly avatarPreview = signal<string | null>(null);
  private avatarFile: File | null = null;

  readonly form = this.fb.nonNullable.group({
    userName: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    // Backend UserRegisterRequest.birthDate is @NotNull; an omitted date 400s. The native
    // date input emits ISO yyyy-MM-dd, which the backend accepts — required just blocks blank.
    birthDate: ['', [Validators.required]],
    isPrivate: [false]
  });

  onAvatar(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.avatarFile = file;
    this.avatarPreview.set(file ? URL.createObjectURL(file) : null);
  }

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    const v = this.form.getRawValue();

    // Register FIRST: the gateway rejects an avatar upload until a token exists.
    this.auth
      .register({
        userName: v.userName,
        email: v.email,
        password: v.password,
        birthDate: v.birthDate || undefined,
        isPrivate: v.isPrivate
      })
      .pipe(
        switchMap((res) => {
          if (!this.session.setToken(res.token)) {
            this.error.set('Registration succeeded but the token was invalid.');
            return EMPTY;
          }
          // Token stored — now (and only now) attach a chosen avatar.
          return this.attachAvatar();
        }),
        finalize(() => this.loading.set(false))
      )
      .subscribe({
        next: () => void this.router.navigateByUrl('/home'),
        error: () => this.error.set('Could not create your account. Try a different email.')
      });
  }

  /**
   * Upload the chosen avatar (now authenticated) and set it on the profile. The account
   * already exists, so an avatar failure must NOT block sign-in: swallow it and let the
   * caller navigate home with the avatar simply unset. No avatar chosen -> a single emit.
   */
  private attachAvatar(): Observable<unknown> {
    const id = this.session.userId();
    if (!this.avatarFile || !id) {
      return of(null);
    }
    return this.media.upload(this.avatarFile).pipe(
      switchMap((uploaded) => this.users.update(id, { profilePictureId: uploaded.id })),
      catchError(() => of(null))
    );
  }
}
