import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthenticationResponse } from '../models/auth.model';
import { UserLoginRequest, UserRegisterRequest } from '../models/user.model';

/**
 * Auth API (user-service). Login is JSON; register is multipart/form-data because the
 * backend's UserRegisterRequest is a multipart payload (avatar pre-uploaded via /media,
 * its id passed as profilePictureId).
 */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);

  login(body: UserLoginRequest): Observable<AuthenticationResponse> {
    return this.http.post<AuthenticationResponse>('/user-service/auth/login', body);
  }

  register(body: UserRegisterRequest): Observable<AuthenticationResponse> {
    const form = new FormData();
    form.append('userName', body.userName);
    form.append('email', body.email);
    form.append('password', body.password);
    // biography is NOT NULL on the backend, so always send it (defaulting to empty — the
    // register form doesn't collect a bio; the user sets it later via edit-profile).
    form.append('biography', body.biography ?? '');
    if (body.birthDate) {
      form.append('birthDate', body.birthDate);
    }
    if (body.isPrivate !== undefined) {
      form.append('isPrivate', String(body.isPrivate));
    }
    if (body.profilePictureId) {
      form.append('profilePictureId', body.profilePictureId);
    }
    return this.http.post<AuthenticationResponse>('/user-service/auth/register', form);
  }
}

export { AuthApiService as AuthService };
