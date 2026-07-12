import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from '../config/api-base-url.token';

/**
 * Prefixes every RELATIVE request URL with the currently-active gateway base URL.
 *
 * Because it reads {@link GatewayRegistry.activeBaseUrl} per request, changing the
 * active gateway reroutes the very next request — the core of the runtime URL swap.
 * Absolute URLs (http/https) and assets/* are passed through untouched, so MSW and
 * the config fetch are unaffected.
 */
export const baseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const apiBaseUrl = inject(API_BASE_URL);

  const isAbsolute = /^https?:\/\//i.test(req.url);
  const isLocalAsset = req.url.startsWith('assets/') || req.url.startsWith('/assets/');
  if (isAbsolute || isLocalAsset) {
    return next(req);
  }

  const base = apiBaseUrl();
  const path = req.url.startsWith('/') ? req.url : `/${req.url}`;
  return next(req.clone({ url: `${base}${path}` }));
};
