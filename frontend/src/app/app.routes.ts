import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guard';

/**
 * Lazy, standalone routes. Authenticated areas live under the 3-column AppShell;
 * auth screens are full-bleed. Edge auth is authoritative — guards are UX only.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage)
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage)
  },
  {
    path: '',
    loadComponent: () => import('./layout/app-shell.component').then((m) => m.AppShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        loadComponent: () => import('./features/feed/feed.page').then((m) => m.FeedPage)
      },
      {
        path: 'compose',
        loadComponent: () => import('./features/compose/compose.page').then((m) => m.ComposePage)
      },
      {
        path: 'tweet/:id',
        loadComponent: () =>
          import('./features/tweet-detail/tweet-detail.page').then((m) => m.TweetDetailPage)
      },
      {
        path: 'profile/:id',
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage)
      },
      {
        path: 'explore',
        loadComponent: () =>
          import('./features/m0/m0-placeholder.page').then((m) => m.M0PlaceholderPage)
      },
      {
        path: 'search',
        loadComponent: () => import('./features/search/search.page').then((m) => m.SearchPage)
      },
      {
        path: 'ai',
        loadComponent: () =>
          import('./features/m0/m0-placeholder.page').then((m) => m.M0PlaceholderPage)
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/m0/m0-placeholder.page').then((m) => m.M0PlaceholderPage)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
