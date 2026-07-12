import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { of, Subject, switchMap } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserSummaryDto } from '../../core/api/models/user.model';
import { UserService } from '../../core/api/services/user.service';
import { AvatarComponent } from '../../shared/ui/avatar/avatar.component';
import { FollowButtonComponent } from '../../shared/ui/follow-button/follow-button.component';
import { RouterLink } from '@angular/router';
import { SessionStore } from '../../core/state/session.store';

/**
 * People search. Mirrors the backend `users/search/{term}` contract: a debounced query
 * (300 ms) that, once at least two characters are typed, returns matching user summaries.
 * Surfaces loading, empty-result and error states. Single-gateway-relative — backend
 * agnostic like the rest of the app.
 */
@Component({
  selector: 'tb-search',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, AvatarComponent, FollowButtonComponent, RouterLink],
  host: { class: 'block' },
  template: `
    <header class="sticky top-0 z-20 border-b border-border bg-background/80 px-4 py-3 backdrop-blur">
      <h1 class="mb-3 text-xl font-bold">Search</h1>
      <div class="flex items-center gap-2 rounded-full bg-secondary px-4 py-2">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM21 21l-4.3-4.3" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <input
          [(ngModel)]="term"
          (ngModelChange)="onTerm($event)"
          type="search"
          autocomplete="off"
          placeholder="Search people"
          aria-label="Search people"
          class="w-full border-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
        />
      </div>
    </header>

    @if (loading()) {
      <div class="px-4 py-8 text-center" data-testid="search-loading">
        <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-muted-foreground border-r-transparent"></span>
      </div>
    } @else if (error()) {
      <div class="px-4 py-12 text-center" role="alert" data-testid="search-error">
        <p class="text-muted-foreground">Could not run that search. Please try again.</p>
      </div>
    } @else if (searched() && results().length === 0) {
      <div class="px-4 py-12 text-center" data-testid="search-empty">
        <h2 class="text-lg font-bold">No results for "{{ lastTerm() }}"</h2>
        <p class="mt-1 text-sm text-muted-foreground">Try searching for something else.</p>
      </div>
    } @else if (results().length > 0) {
      <ul data-testid="search-results">
        @for (u of results(); track u.id) {
          <li class="flex items-center gap-3 border-b border-border px-4 py-3 transition-colors hover:bg-accent/30">
            <a [routerLink]="['/profile', u.id]" class="shrink-0">
              <tb-avatar [name]="u.user_name ?? '?'" size="md" />
            </a>
            <a [routerLink]="['/profile', u.id]" class="min-w-0 flex-1">
              <p class="truncate font-bold">{{ u.user_name }}</p>
              <p class="truncate text-sm text-muted-foreground">&#64;{{ u.user_name }}</p>
            </a>
            <tb-follow-button [meId]="meId()" [targetId]="u.id ?? ''" size="sm" />
          </li>
        }
      </ul>
    } @else {
      <p class="px-4 py-12 text-center text-sm text-muted-foreground" data-testid="search-idle">
        Search for people on Tweebyte.
      </p>
    }
  `
})
export class SearchPage implements OnInit {
  private readonly users = inject(UserService);
  private readonly session = inject(SessionStore);
  private readonly destroyRef = inject(DestroyRef);

  private readonly query$ = new Subject<string>();

  term = '';
  readonly results = signal<UserSummaryDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly searched = signal(false);
  readonly lastTerm = signal('');

  readonly meId = this.session.userId;

  ngOnInit(): void {
    this.query$
      .pipe(
        map((t) => t.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        filter((t) => {
          if (t.length < 2) {
            // Too short to query: clear results, reset the searched flag.
            this.results.set([]);
            this.searched.set(false);
            this.loading.set(false);
            return false;
          }
          this.loading.set(true);
          this.error.set(false);
          this.lastTerm.set(t);
          return true;
        }),
        // Catch INSIDE switchMap so a failed search does not terminate the outer stream
        // (the next keystroke must still query). null signals "this search failed".
        switchMap((t) =>
          this.users.search(t).pipe(catchError(() => of(null)))
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((rows) => {
        this.loading.set(false);
        this.searched.set(true);
        if (rows === null) {
          this.results.set([]);
          this.error.set(true);
        } else {
          this.results.set(rows);
          this.error.set(false);
        }
      });
  }

  onTerm(value: string): void {
    this.query$.next(value);
  }
}
