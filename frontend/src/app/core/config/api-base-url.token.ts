import { inject, InjectionToken, Signal } from '@angular/core';

import { GatewayRegistry } from './gateway-registry.service';

/**
 * Active gateway base URL as a signal. Consumers read it per request so changing the
 * active gateway takes effect immediately without rebuilding or reloading the app.
 */
export const API_BASE_URL = new InjectionToken<Signal<string>>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => inject(GatewayRegistry).activeBaseUrl
});
