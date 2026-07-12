import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoginPage } from './login.page';
import { AuthService } from '../../core/api/services/auth.service';
import { SessionStore } from '../../core/state/session.store';

function makeToken(userId: string): string {
  const enc = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ user_id: userId, exp: Math.floor(Date.now() / 1000) + 3600 })}.s`;
}

interface RenderOpts {
  login?: ReturnType<typeof vi.fn>;
}

async function renderLogin(opts: RenderOpts = {}) {
  const login = opts.login ?? vi.fn().mockReturnValue(of({ token: makeToken('me') }));
  const auth = { login };
  const result = await render(LoginPage, {
    providers: [provideRouter([]), { provide: AuthService, useValue: auth }]
  });
  return { ...result, auth, login };
}

describe('LoginPage', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders the sign-in heading, email + password inputs, and neutral copy', async () => {
    await renderLogin();
    expect(screen.getByText('Sign in to Tweebyte')).toBeTruthy();
    expect(document.querySelector('input[formcontrolname="email"]')).toBeTruthy();
    expect(document.querySelector('input[formcontrolname="password"]')).toBeTruthy();
    // NEUTRALITY: the page copy must not name any backend implementation.
    expect(document.body.textContent).not.toMatch(/async|reactive|webflux|mvc|blocking|event-loop/i);
  });

  it('does nothing on submit while the form is invalid', async () => {
    const { fixture, login } = await renderLogin();
    // Empty form -> invalid.
    fixture.componentInstance.submit();
    expect(login).not.toHaveBeenCalled();
  });

  it('logs in, stores the session, and navigates to /home', async () => {
    const login = vi.fn().mockReturnValue(of({ token: makeToken('me') }));
    const { fixture } = await renderLogin({ login });
    const router = fixture.debugElement.injector.get(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    fixture.componentInstance.form.setValue({ email: 'a@b.dev', password: 'pw' });
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(login).toHaveBeenCalledWith({ email: 'a@b.dev', password: 'pw' });
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(true);
    expect(navigate).toHaveBeenCalledWith('/home');
  });

  it('surfaces an inline error on a 401 and clears the loading flag', async () => {
    const login = vi.fn().mockReturnValue(throwError(() => ({ status: 401 })));
    const { fixture } = await renderLogin({ login });

    fixture.componentInstance.form.setValue({ email: 'a@b.dev', password: 'pw' });
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(fixture.componentInstance.error()).toBeTruthy();
    expect(typeof fixture.componentInstance.error()).toBe('string');
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('reports an error when the returned token cannot be decoded', async () => {
    const login = vi.fn().mockReturnValue(of({ token: 'garbage' }));
    const { fixture } = await renderLogin({ login });

    fixture.componentInstance.form.setValue({ email: 'a@b.dev', password: 'pw' });
    fixture.componentInstance.submit();
    await fixture.whenStable();

    expect(fixture.componentInstance.error()).toBeTruthy();
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
  });
});
