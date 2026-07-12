import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RegisterPage } from './register.page';
import { AuthService } from '../../core/api/services/auth.service';
import { MediaService } from '../../core/api/services/media.service';
import { UserService } from '../../core/api/services/user.service';
import { SessionStore } from '../../core/state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

const VALID = { userName: 'ada', email: 'a@b.dev', password: 'password1', birthDate: '1995-04-21', isPrivate: false };

interface RenderOpts {
  register?: ReturnType<typeof vi.fn>;
  upload?: ReturnType<typeof vi.fn>;
  update?: ReturnType<typeof vi.fn>;
}

async function renderRegister(opts: RenderOpts = {}) {
  const register = opts.register ?? vi.fn().mockReturnValue(of({ token: makeToken('me') }));
  const upload = opts.upload ?? vi.fn().mockReturnValue(of({ id: 'pic-1' }));
  const update = opts.update ?? vi.fn().mockReturnValue(of({ id: 'me' }));
  const auth = { register };
  const media = { upload };
  const users = { update };
  const result = await render(RegisterPage, {
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: auth },
      { provide: MediaService, useValue: media },
      { provide: UserService, useValue: users }
    ]
  });
  return { ...result, auth, media, users, register, upload, update };
}

describe('RegisterPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    // jsdom has no real object-URL backing; the page calls it on avatar select.
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview');
  });

  it('renders the create-account heading and the username/email/password fields', async () => {
    await renderRegister();
    expect(screen.getByText('Create your account')).toBeTruthy();
    expect(document.querySelector('input[formcontrolname="userName"]')).toBeTruthy();
    expect(document.querySelector('input[formcontrolname="email"]')).toBeTruthy();
    expect(document.querySelector('input[formcontrolname="password"]')).toBeTruthy();
  });

  it('does nothing on submit while the form is invalid', async () => {
    const { fixture, register } = await renderRegister();
    fixture.componentInstance.submit();
    expect(register).not.toHaveBeenCalled();
  });

  it('keeps the form invalid (submit blocked) while birthDate is blank', async () => {
    // Backend requires birthDate (@NotNull); a blank date must not reach the server.
    const { fixture, register } = await renderRegister();
    fixture.componentInstance.form.setValue({ ...VALID, birthDate: '' });
    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.submit();
    expect(register).not.toHaveBeenCalled();
  });

  it('sends the ISO yyyy-MM-dd birthDate verbatim (no reformatting)', async () => {
    const { fixture, register } = await renderRegister();
    vi.spyOn(fixture.debugElement.injector.get(Router), 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(register).toHaveBeenCalledTimes(1);
    expect(register.mock.calls[0][0].birthDate).toBe('1995-04-21');
  });

  it('registers without an avatar: no upload, no profile update, navigates home', async () => {
    const { fixture, register, upload, update } = await renderRegister();
    const router = fixture.debugElement.injector.get(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(upload).not.toHaveBeenCalled();
    expect(update).not.toHaveBeenCalled();
    expect(register).toHaveBeenCalledTimes(1);
    const body = register.mock.calls[0][0];
    expect(body).toMatchObject({ userName: 'ada', email: 'a@b.dev', password: 'password1' });
    expect(body.profilePictureId).toBeUndefined();
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(true);
    expect(navigate).toHaveBeenCalledWith('/home');
  });

  it('registers FIRST, then uploads the avatar and attaches it via profile update', async () => {
    const { fixture, register, upload, update } = await renderRegister();
    const navigate = vi
      .spyOn(fixture.debugElement.injector.get(Router), 'navigateByUrl')
      .mockResolvedValue(true);

    const file = new File(['x'], 'a.png', { type: 'image/png' });
    fixture.componentInstance.onAvatar({ target: { files: [file] } } as unknown as Event);

    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    await fixture.whenStable();

    // The gateway rejects unauthenticated uploads, so register must run before the upload
    // and the register body must NOT carry a profilePictureId.
    expect(register).toHaveBeenCalledTimes(1);
    expect(register.mock.calls[0][0].profilePictureId).toBeUndefined();
    expect(register.mock.invocationCallOrder[0]).toBeLessThan(upload.mock.invocationCallOrder[0]);

    // Avatar attached against the now-authenticated owner id from the token (user_id 'me').
    expect(upload).toHaveBeenCalledTimes(1);
    expect(upload).toHaveBeenCalledWith(file);
    expect(update).toHaveBeenCalledTimes(1);
    expect(update).toHaveBeenCalledWith('me', { profilePictureId: 'pic-1' });
    expect(navigate).toHaveBeenCalledWith('/home');
  });

  it('still signs in and navigates home when the avatar upload fails', async () => {
    const upload = vi.fn().mockReturnValue(throwError(() => ({ status: 401 })));
    const { fixture, update } = await renderRegister({ upload });
    const navigate = vi
      .spyOn(fixture.debugElement.injector.get(Router), 'navigateByUrl')
      .mockResolvedValue(true);

    const file = new File(['x'], 'a.png', { type: 'image/png' });
    fixture.componentInstance.onAvatar({ target: { files: [file] } } as unknown as Event);

    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    await fixture.whenStable();

    // Account already exists — a failed avatar must not block login or surface an error.
    expect(update).not.toHaveBeenCalled();
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(true);
    expect(fixture.componentInstance.error()).toBeNull();
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/home');
  });

  it('surfaces an error and clears loading when registration fails', async () => {
    const register = vi.fn().mockReturnValue(throwError(() => ({ status: 400 })));
    const { fixture } = await renderRegister({ register });

    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(fixture.componentInstance.error()).toBeTruthy();
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('errors without uploading or navigating when the returned token is unusable', async () => {
    const register = vi.fn().mockReturnValue(of({ token: 'not-a-jwt' }));
    const { fixture, upload, update } = await renderRegister({ register });
    const navigate = vi
      .spyOn(fixture.debugElement.injector.get(Router), 'navigateByUrl')
      .mockResolvedValue(true);

    const file = new File(['x'], 'a.png', { type: 'image/png' });
    fixture.componentInstance.onAvatar({ target: { files: [file] } } as unknown as Event);

    fixture.componentInstance.form.setValue(VALID);
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(upload).not.toHaveBeenCalled();
    expect(update).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(fixture.componentInstance.error()).toBeTruthy();
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
