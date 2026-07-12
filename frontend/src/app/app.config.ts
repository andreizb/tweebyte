import {
  APP_INITIALIZER,
  ApplicationConfig,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { loadAppConfig } from './core/config/config-loader';
import { baseUrlInterceptor } from './core/http/base-url.interceptor';
import { authInterceptor } from './core/http/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { latencyInterceptor } from './core/http/latency.interceptor';

/**
 * Root application providers. Angular 17 idioms throughout:
 *  - functional interceptors via withInterceptors (ORDER MATTERS: base-url first to
 *    rewrite the URL, then auth to attach the bearer, then error to catch responses)
 *  - withFetch() so HttpClient uses the Fetch backend (streaming-friendly, aligns with
 *    our SSE-over-POST fetch usage)
 *  - provideAppInitializer to load assets/config.json + boot MSW before first request
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })
    ),
    provideHttpClient(
      withFetch(),
      withInterceptors([
        baseUrlInterceptor,
        authInterceptor,
        latencyInterceptor,
        errorInterceptor
      ])
    ),
    provideAnimations(),
    {
      provide: APP_INITIALIZER,
      useFactory: loadAppConfig,
      multi: true
    }
  ]
};
