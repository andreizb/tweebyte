import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { EditProfileDialogComponent } from './edit-profile.dialog';
import { UserDto } from '../../core/api/models/user.model';
import { UserService } from '../../core/api/services/user.service';
import { MediaService } from '../../core/api/services/media.service';

const USER: UserDto = {
  id: 'me',
  user_name: 'ada',
  biography: 'enchantress of numbers',
  is_private: false
};

interface Opts {
  update?: ReturnType<typeof vi.fn>;
  get?: ReturnType<typeof vi.fn>;
  upload?: ReturnType<typeof vi.fn>;
}

async function renderDialog(opts: Opts = {}) {
  // PUT /users/{id} is 204 No Content, so update() resolves to void; the dialog re-reads
  // GET /users/{id} for the fresh DTO it emits.
  const update = opts.update ?? vi.fn().mockReturnValue(of(undefined));
  const get = opts.get ?? vi.fn().mockReturnValue(of({ ...USER, biography: 'updated' }));
  const upload = opts.upload ?? vi.fn().mockReturnValue(of({ id: 'pic-9' }));
  const saved = vi.fn();
  const closed = vi.fn();
  const result = await render(EditProfileDialogComponent, {
    componentInputs: { user: USER },
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: UserService, useValue: { update, get } },
      { provide: MediaService, useValue: { upload } }
    ]
  });
  result.fixture.componentInstance.saved.subscribe(saved);
  result.fixture.componentInstance.close.subscribe(closed);
  return { ...result, update, get, upload, saved, closed };
}

describe('EditProfileDialogComponent', () => {
  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview');
  });

  it('pre-fills the form from the current user', async () => {
    const { fixture } = await renderDialog();
    const v = fixture.componentInstance.form.getRawValue();
    expect(v.userName).toBe('ada');
    expect(v.biography).toBe('enchantress of numbers');
    expect(v.isPrivate).toBe(false);
  });

  it('renders the dialog with an accessible label', async () => {
    await renderDialog();
    expect(screen.getByRole('dialog', { name: 'Edit profile' })).toBeTruthy();
  });

  it('saves without an avatar: no upload, multipart update, re-reads and emits the fresh user', async () => {
    const fresh = { ...USER, user_name: 'ada2', biography: 'new bio', is_private: true };
    const get = vi.fn().mockReturnValue(of(fresh));
    const { fixture, update, upload, saved } = await renderDialog({ get });
    fixture.componentInstance.form.patchValue({ userName: 'ada2', biography: 'new bio', isPrivate: true });
    fixture.componentInstance.save();
    await fixture.whenStable();

    expect(upload).not.toHaveBeenCalled();
    expect(update).toHaveBeenCalledTimes(1);
    const [id, body] = update.mock.calls[0];
    expect(id).toBe('me');
    expect(body).toMatchObject({ userName: 'ada2', biography: 'new bio', isPrivate: true });
    expect(body.profilePictureId).toBeUndefined();
    // The 204 carries no DTO: it re-reads GET /users/{id} and emits that.
    expect(get).toHaveBeenCalledWith('me');
    expect(saved).toHaveBeenCalledTimes(1);
    expect(saved).toHaveBeenCalledWith(fresh);
  });

  it('pre-uploads a chosen avatar and passes its id as profilePictureId', async () => {
    const { fixture, update, upload } = await renderDialog();
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    fixture.componentInstance.onAvatar({ target: { files: [file] } } as unknown as Event);

    fixture.componentInstance.save();
    await fixture.whenStable();

    expect(upload).toHaveBeenCalledTimes(1);
    expect(upload).toHaveBeenCalledWith(file);
    expect(update.mock.calls[0][1].profilePictureId).toBe('pic-9');
  });

  it('keeps the dialog open and does not emit saved when update fails', async () => {
    const get = vi.fn().mockReturnValue(of(USER));
    const { fixture, saved } = await renderDialog({
      update: vi.fn().mockReturnValue(throwError(() => ({ status: 500 }))),
      get
    });
    fixture.componentInstance.save();
    await fixture.whenStable();
    expect(saved).not.toHaveBeenCalled();
    expect(get).not.toHaveBeenCalled(); // never re-reads when the write failed
    expect(fixture.componentInstance.saving()).toBe(false);
  });

  it('keeps the dialog open and does not emit saved when the post-save re-read fails', async () => {
    const { fixture, update, saved } = await renderDialog({
      get: vi.fn().mockReturnValue(throwError(() => ({ status: 500 })))
    });
    fixture.componentInstance.save();
    await fixture.whenStable();
    expect(update).toHaveBeenCalledTimes(1);
    expect(saved).not.toHaveBeenCalled();
    expect(fixture.componentInstance.saving()).toBe(false);
  });

  it('does nothing on save while the form is invalid (blank username)', async () => {
    const { fixture, update } = await renderDialog();
    fixture.componentInstance.form.patchValue({ userName: '' });
    fixture.componentInstance.save();
    expect(update).not.toHaveBeenCalled();
  });

  it('emits close on Escape', async () => {
    const { fixture, closed } = await renderDialog();
    fixture.componentInstance.onEscape();
    expect(closed).toHaveBeenCalledTimes(1);
  });
});
