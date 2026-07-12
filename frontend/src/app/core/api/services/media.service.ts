import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, from, shareReplay, tap } from 'rxjs';
import { GatewayRegistry } from '../../config/gateway-registry.service';
import { SessionStore } from '../../state/session.store';
import { MediaAccessRequest, MediaExistsResponse, MediaIdResponse } from '../models/media.model';

/** A shared, ref-counted blob URL for one media id. */
interface BlobEntry {
  /** Replayed object-URL stream; one bearer fetch shared by every holder of this id. */
  readonly stream: Observable<string>;
  /** Holders that have acquired this id and not yet released it. */
  refs: number;
  /** The resolved object URL once fetched, captured so it can be revoked at the last release. */
  readonly resolved: { url: string | null };
}

/**
 * Media API (user-service).
 *
 * GET /media/{id} is bearer-protected and returns 206 ranged BYTES — an <img src> can't
 * send the Authorization header, so we fetch the bytes ourselves and hand back an object
 * URL. Blob URLs are cached and ref-counted per media id: holders {@link acquire} the URL
 * and {@link release} it; the object URL is revoked once the last holder lets go, so
 * nothing leaks when an avatar changes or a card is destroyed.
 */
@Injectable({ providedIn: 'root' })
export class MediaApiService {
  private readonly http = inject(HttpClient);
  private readonly registry = inject(GatewayRegistry);
  private readonly session = inject(SessionStore);

  private readonly blobCache = new Map<string, BlobEntry>();

  /** Upload a file -> { id }. */
  upload(file: File): Observable<MediaIdResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<MediaIdResponse>('/user-service/media', form);
  }

  preview(id: string, body: MediaAccessRequest): Observable<MediaIdResponse> {
    return this.http.post<MediaIdResponse>(`/user-service/media/${id}/preview`, body);
  }

  reveal(id: string, body: MediaAccessRequest): Observable<Blob> {
    return this.http.post(`/user-service/media/${id}/reveal`, body, {
      responseType: 'blob'
    });
  }

  download(id: string): Observable<Blob> {
    return this.http.get(`/user-service/media/${id}`, { responseType: 'blob' });
  }

  exists(id: string): Observable<MediaExistsResponse> {
    return this.http.get<MediaExistsResponse>(`/user-service/media/${id}/exists`);
  }

  /**
   * Resolve a media id to a displayable object URL (bearer-aware blob fetch), cached and
   * shared across holders. Does NOT take a ref — use {@link acquire}/{@link release} from
   * a view so the URL is revoked when the last holder lets go.
   */
  blobUrl(id: string): Observable<string> {
    return this.entry(id).stream;
  }

  /** Take a ref on a media id's blob URL; pair every call with {@link release}. */
  acquire(id: string): Observable<string> {
    const entry = this.entry(id);
    entry.refs += 1;
    return entry.stream;
  }

  /** Drop a ref taken via {@link acquire}; revokes + forgets the URL at the last release. */
  release(id: string): void {
    const entry = this.blobCache.get(id);
    if (!entry) {
      return;
    }
    entry.refs -= 1;
    if (entry.refs <= 0) {
      this.dispose(id, entry);
    }
  }

  /** Revoke + drop a cached blob URL regardless of refs (e.g. an avatar was replaced). */
  invalidate(id: string): void {
    const entry = this.blobCache.get(id);
    if (entry) {
      this.dispose(id, entry);
    }
  }

  private entry(id: string): BlobEntry {
    const cached = this.blobCache.get(id);
    if (cached) {
      return cached;
    }
    const resolved: { url: string | null } = { url: null };
    const entry: BlobEntry = {
      refs: 0,
      resolved,
      stream: from(this.fetchBlobUrl(id)).pipe(
        tap((url) => {
          resolved.url = url;
        }),
        shareReplay(1)
      )
    };
    this.blobCache.set(id, entry);
    return entry;
  }

  private dispose(id: string, entry: BlobEntry): void {
    this.blobCache.delete(id);
    if (entry.resolved.url) {
      URL.revokeObjectURL(entry.resolved.url);
    }
  }

  private async fetchBlobUrl(id: string): Promise<string> {
    const base = this.registry.activeBaseUrl();
    const token = this.session.token();
    const res = await fetch(`${base}/user-service/media/${id}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    });
    if (!res.ok) {
      throw new Error(`media ${id} failed: ${res.status}`);
    }
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  }
}

export { MediaApiService as MediaService };
