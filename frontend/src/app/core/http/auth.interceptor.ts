import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SessionStore } from '../state/session.store';

/**
 * Attaches `Authorization: Bearer <token>` from the SessionStore.
 *
 * No cookies are ever sent (the gateways strip them). The bearer is read from the
 * store per request, so it survives a backend swap unchanged. Auth/anonymous endpoints
 * (login/register) carry no token, which is correct — they are sent without a header
 * when none is present.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionStore);
  const token = session.token();

  if (!token || req.headers.has('Authorization')) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    })
  );
};
