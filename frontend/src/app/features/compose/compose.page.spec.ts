import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ComposePage } from './compose.page';
import { TweetService } from '../../core/api/services/tweet.service';

async function renderCompose() {
  const tweetSvc = { create: vi.fn().mockReturnValue(of({ id: 't9', content: 'a brand new post' })) };
  const result = await render(ComposePage, {
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: TweetService, useValue: tweetSvc }
    ]
  });
  return result;
}

describe('ComposePage', () => {
  beforeEach(() => sessionStorage.clear());

  it('renders the compose header and a compose box', async () => {
    await renderCompose();
    expect(screen.getByText('New post')).toBeTruthy();
    expect(screen.getByLabelText('Compose a new post')).toBeTruthy();
  });

  it('navigates home after a successful post', async () => {
    const { fixture } = await renderCompose();
    const router = fixture.debugElement.injector.get(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.onPosted({ id: 't9' });
    expect(navSpy).toHaveBeenCalledWith(['/home']);
  });

  it('back() pops the location history', async () => {
    const { fixture } = await renderCompose();
    const location = fixture.debugElement.injector.get(Location);
    const backSpy = vi.spyOn(location, 'back').mockImplementation(() => undefined);
    fixture.componentInstance.back();
    expect(backSpy).toHaveBeenCalled();
  });
});
