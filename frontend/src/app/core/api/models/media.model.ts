/**
 * Media domain models — user-service media endpoints.
 *
 * POST /media (multipart) -> { id }
 * POST /media/{id}/preview ({ password }) -> { id }
 * POST /media/{id}/reveal -> raw bytes
 * GET  /media/{id} -> 206 ranged bytes (bearer-protected; fetched as a blob URL by the UI)
 * GET  /media/{id}/exists -> boolean
 */

/** Response of POST /media and POST /media/{id}/preview. */
export interface MediaIdResponse {
  id: string;
}

/** Response of GET /media/{id}/exists. */
export interface MediaExistsResponse {
  exists: boolean;
}

/** POST /media/{id}/preview and /reveal body. */
export interface MediaAccessRequest {
  password: string;
}
