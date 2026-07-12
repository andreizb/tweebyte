import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { UserDto, UserSummaryDto, UserUpdateRequest } from '../models/user.model';

/** Users API (user-service). */
@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly http = inject(HttpClient);

  get(id: string): Observable<UserDto> {
    return this.http.get<UserDto>(`/user-service/users/${id}`);
  }

  summary(id: string): Observable<UserSummaryDto> {
    return this.http.get<UserSummaryDto>(`/user-service/users/summary/${id}`);
  }

  /** Batch lean lookups — one round-trip for a whole set of author ids. */
  summaries(ids: string[]): Observable<UserSummaryDto[]> {
    return this.http.post<UserSummaryDto[]>('/user-service/users/summaries', ids);
  }

  summaryByName(name: string): Observable<UserSummaryDto> {
    return this.http.get<UserSummaryDto>(`/user-service/users/summary/name/${encodeURIComponent(name)}`);
  }

  search(term: string, page = 0, size = 20): Observable<UserSummaryDto[]> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<UserSummaryDto[]>(
      `/user-service/users/search/${encodeURIComponent(term)}`,
      { params }
    );
  }

  /**
   * Owner-gated; multipart (avatar via profilePictureId). PUT /users/{id} responds
   * 204 No Content (UserController @ResponseStatus(NO_CONTENT)) — there is no echoed
   * DTO, so callers must re-read the user to see the saved state.
   */
  update(id: string, body: UserUpdateRequest): Observable<void> {
    const form = new FormData();
    Object.entries(body).forEach(([k, v]) => {
      if (v !== undefined && v !== null) {
        form.append(k, String(v));
      }
    });
    return this.http.put<void>(`/user-service/users/${id}`, form);
  }

  /** Owner-gated. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/user-service/users/${id}`);
  }
}

export { UserApiService as UserService };
