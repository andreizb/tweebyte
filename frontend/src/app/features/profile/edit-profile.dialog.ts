import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  inject,
  input,
  OnInit,
  output,
  signal
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { switchMap, of, Observable, finalize } from 'rxjs';
import { MediaIdResponse } from '../../core/api/models/media.model';
import { UserDto } from '../../core/api/models/user.model';
import { MediaService } from '../../core/api/services/media.service';
import { UserService } from '../../core/api/services/user.service';
import { ToastService } from '../../core/state/toast.service';
import { ButtonComponent } from '../../shared/ui/button/button.component';

/**
 * Edit-profile dialog. Owner-gated multipart PUT /users/{id}: optional avatar is
 * pre-uploaded via POST /media (its id passed as profilePictureId), then the profile
 * fields are saved. The PUT returns 204 No Content, so on success we re-read GET /users/{id}
 * and emit that fresh user for the profile page to refresh in place.
 * On error the toast surfaces and the dialog stays open so edits aren't lost.
 */
@Component({
  selector: 'tb-edit-profile-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonComponent],
  template: `
    <div
      class="fixed inset-0 z-50 grid place-items-center p-4"
      data-testid="edit-profile-dialog"
    >
      <!-- Click-to-dismiss backdrop: a real button so it is keyboard-focusable and the
           dismiss affordance is exposed to assistive tech without an a11y violation. -->
      <button
        type="button"
        class="absolute inset-0 -z-10 h-full w-full cursor-default bg-black/50"
        aria-label="Close dialog"
        (click)="close.emit()"
      ></button>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Edit profile"
        class="w-full max-w-lg rounded-2xl border border-border bg-background p-5 shadow-xl"
      >
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-xl font-bold">Edit profile</h2>
          <button
            type="button"
            class="grid h-9 w-9 place-items-center rounded-full hover:bg-accent"
            (click)="close.emit()"
            aria-label="Close"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6 6 18M6 6l12 12" stroke-linecap="round" />
            </svg>
          </button>
        </div>

        <form [formGroup]="form" (ngSubmit)="save()" class="flex flex-col gap-4" novalidate>
          <div class="flex items-center gap-3">
            <label class="grid h-16 w-16 shrink-0 cursor-pointer place-items-center overflow-hidden rounded-full border border-dashed border-input bg-secondary text-xs text-muted-foreground hover:border-ring">
              @if (avatarPreview()) {
                <img [src]="avatarPreview()!" alt="New avatar preview" class="h-full w-full object-cover" />
              } @else {
                <span>Avatar</span>
              }
              <input type="file" accept="image/*" class="hidden" (change)="onAvatar($event)" aria-label="Change avatar" />
            </label>
            <p class="text-xs text-muted-foreground">Optional — pre-uploaded via /media before saving.</p>
          </div>

          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium">Username</span>
            <input
              formControlName="userName"
              class="h-11 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
            />
          </label>

          <label class="flex flex-col gap-1.5">
            <span class="text-sm font-medium">Bio</span>
            <textarea
              formControlName="biography"
              rows="3"
              maxlength="160"
              class="resize-none rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/40"
            ></textarea>
          </label>

          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" formControlName="isPrivate" class="h-4 w-4 rounded border-input accent-brand" />
            Private account
          </label>

          <div class="mt-1 flex justify-end gap-2">
            <button tbButton variant="outline" size="md" type="button" (click)="close.emit()">Cancel</button>
            <button tbButton variant="brand" size="md" type="submit" [loading]="saving()" [disabled]="form.invalid || saving()">
              Save
            </button>
          </div>
        </form>
      </div>
    </div>
  `
})
export class EditProfileDialogComponent implements OnInit {
  readonly user = input.required<UserDto>();
  readonly saved = output<UserDto>();
  readonly close = output<void>();

  private readonly fb = inject(FormBuilder);
  private readonly users = inject(UserService);
  private readonly media = inject(MediaService);
  private readonly toast = inject(ToastService);

  readonly saving = signal(false);
  readonly avatarPreview = signal<string | null>(null);
  private avatarFile: File | null = null;

  readonly form = this.fb.nonNullable.group({
    userName: ['', [Validators.required, Validators.minLength(2)]],
    biography: [''],
    isPrivate: [false]
  });

  ngOnInit(): void {
    const u = this.user();
    this.form.patchValue({
      userName: u.user_name ?? '',
      biography: u.biography ?? '',
      isPrivate: !!u.is_private
    });
  }

  /**
   * Escape-to-close, bound at the host so it works regardless of which inner control
   * holds focus, and without putting a keyboard handler on a non-focusable element.
   */
  @HostListener('keydown.escape')
  onEscape(): void {
    this.close.emit();
  }

  onAvatar(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.avatarFile = file;
    this.avatarPreview.set(file ? URL.createObjectURL(file) : null);
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const id = this.user().id ?? '';

    const avatar$: Observable<MediaIdResponse | null> = this.avatarFile
      ? this.media.upload(this.avatarFile)
      : of(null);

    avatar$
      .pipe(
        switchMap((uploaded) =>
          this.users.update(id, {
            userName: v.userName,
            biography: v.biography,
            isPrivate: v.isPrivate,
            profilePictureId: uploaded?.id
          })
        ),
        // PUT /users/{id} returns 204 No Content (no echoed DTO). Re-read the user so the
        // profile refreshes from the authoritative server state rather than a guessed merge.
        switchMap(() => this.users.get(id)),
        finalize(() => this.saving.set(false))
      )
      .subscribe({
        next: (updated) => {
          this.toast.success('Profile updated');
          this.saved.emit(updated);
        },
        error: () => this.toast.error('Could not save profile', 'Please try again.')
      });
  }
}
