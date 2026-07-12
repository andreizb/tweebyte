# syntax=docker/dockerfile:1
# Tweebyte frontend — multi-stage: build the Angular production bundle, then serve it
# (and same-origin reverse-proxy the API/actuator to the gateway) with nginx.

# --- Stage 1: build ---------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Mandatory quality gates — the image FAILS to build if ESLint, the unit tests, or the 90%
# coverage threshold (vitest.config.mts) fail. Mirrors the backend's bound Maven verify gates,
# so "building the project" enforces frontend quality the same way the services do.
RUN npm run lint
RUN npm run test
# defaultConfiguration=production (angular.json) → optimized, hashed, no source maps.
RUN npm run build

# --- Stage 2: serve ---------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/tweebyte/browser /usr/share/nginx/html
# Prod runtime config (real backend, same-origin) overrides the bundled dev config.
COPY deploy/config.json /usr/share/nginx/html/assets/config.json
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
    CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
