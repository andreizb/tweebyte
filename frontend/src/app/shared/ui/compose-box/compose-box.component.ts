import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TweetDto } from '../../../core/api/models/tweet.model';
import { TweetService } from '../../../core/api/services/tweet.service';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../core/state/toast.service';
import { AvatarComponent } from '../avatar/avatar.component';
import { ButtonComponent } from '../button/button.component';

const MIN_LEN = 10;
const MAX_LEN = 280;

/**
 * ComposeBox — posts a tweet (POST /tweets/{uid}). Enforces the backend's >=10 char
 * minimum client-side and a 280 ceiling, with a live ring counter. Emits the created
 * tweet so the feed can optimistically prepend it.
 */
@Component({
  selector: 'tb-compose-box',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, AvatarComponent, ButtonComponent],
  template: `
    <div class="flex gap-3 px-4 py-3">
      <tb-avatar [name]="username()" size="md" class="mt-1" />
      <div class="min-w-0 flex-1">
        <textarea
          [(ngModel)]="text"
          (input)="onInput()"
          [placeholder]="placeholder()"
          rows="2"
          class="w-full resize-none border-0 bg-transparent py-2 text-xl outline-none placeholder:text-muted-foreground"
          [attr.maxlength]="max + 40"
          aria-label="Compose a new post"
        ></textarea>

        <div class="flex items-center justify-between border-t border-border pt-2">
          <span class="text-xs text-muted-foreground">
            {{ tooShort() ? (min - len()) + ' more to post' : '' }}
          </span>
          <div class="flex items-center gap-3">
            @if (len() > 0) {
              <span
                class="text-xs tabular-nums"
                [class.text-muted-foreground]="remaining() > 20"
                [class.text-warn]="remaining() <= 20 && remaining() >= 0"
                [class.text-destructive]="remaining() < 0"
                >{{ remaining() }}</span
              >
            }
            <button tbButton variant="brand" size="md" [loading]="posting()" [disabled]="!canPost()" (click)="post()">
              Post
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ComposeBoxComponent {
  readonly placeholder = input('What is happening?!');
  readonly posted = output<TweetDto>();

  private readonly tweets = inject(TweetService);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  readonly username = this.session.username;
  text = '';
  readonly min = MIN_LEN;
  readonly max = MAX_LEN;

  readonly len = signal(0);
  readonly posting = signal(false);

  readonly remaining = computed(() => this.max - this.len());
  readonly tooShort = computed(() => this.len() > 0 && this.len() < this.min);
  readonly canPost = computed(() => this.len() >= this.min && this.len() <= this.max && !this.posting());

  onInput(): void {
    this.len.set(this.text.trim().length);
  }

  post(): void {
    const me = this.session.userId();
    const content = this.text.trim();
    if (!me || !this.canPost()) {
      return;
    }
    this.posting.set(true);
    this.tweets.create(me, { content }).subscribe({
      next: (created) => {
        this.posting.set(false);
        // POST /tweets returns {id} ONLY — render an optimistic card from local state so the
        // prepended tweet shows the author/body/timestamp immediately (the canonical card
        // shape arrives on the next feed reload). Without this it renders "Unknown / @unknown".
        this.posted.emit({
          id: created.id,
          user_id: me,
          content,
          created_at: new Date().toISOString(),
          likes_count: 0,
          replies_count: 0,
          retweets_count: 0,
          media_ids: [],
          user: { id: me, user_name: this.session.username() ?? undefined }
        });
        this.text = '';
        this.len.set(0);
        this.toast.success('Posted');
      },
      error: () => {
        this.posting.set(false);
        this.toast.error('Could not post', 'Please try again.');
      }
    });
  }
}
