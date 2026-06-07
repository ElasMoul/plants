import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { User, AuthTokenPayload } from '../models/user.model';

interface LoginRequest {
  email: string;
  password: string;
}

interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

interface AuthResponse {
  token: string;
  user: User;
}

const TOKEN_KEY = 'plantpal_token';
const USER_KEY  = 'plantpal_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.baseUrl}/login`, request)
      .pipe(tap(res => this.storeSession(res.data)));
  }

  register(request: RegisterRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.baseUrl}/register`, request)
      .pipe(tap(res => this.storeSession(res.data)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  isLoggedIn(): boolean {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return false;
    return !this.isTokenExpired(token);
  }

  getToken(): string | null {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token || this.isTokenExpired(token)) {
      this.logout();
      return null;
    }
    return token;
  }

  getCurrentUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }

  private storeSession(data: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = this.decodeToken(token);
      return Date.now() >= payload.exp * 1000;
    } catch {
      return true;
    }
  }

  private decodeToken(token: string): AuthTokenPayload {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(base64);
    return JSON.parse(json) as AuthTokenPayload;
  }
}
