import { inject, Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { API_BASE_URL, ApiResponse } from "@plantpal/shared-core";
import { map } from "rxjs";

export interface AdminAccess {
  userId: number;
  name: string;
  administrator: boolean;
}
export interface AdminPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}
export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  role: "USER" | "ADMIN";
  businessTier: boolean;
  createdAt: string;
  visionModel: string;
  reasoningModel: string;
  version: number;
}
export interface AdminModel {
  id: string;
  capability: string;
  model: string;
  visible: boolean;
  configured: boolean;
  version: number;
}
export interface AdminActivity {
  id: number;
  actorId: number;
  action: string;
  target: string;
  createdAt: string;
}
export interface AdminOverview {
  totals: Record<string, number>;
  scans: { date: string; completed: number; failed: number; pending: number }[];
  refreshedAt: string;
}

@Injectable({ providedIn: "root" })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  access() {
    return this.http
      .get<ApiResponse<AdminAccess>>(`${this.base}/users/me/access`)
      .pipe(map((r) => r.data));
  }
  overview() {
    return this.get<AdminOverview>("overview");
  }
  users(query: string, status: string, page: number) {
    let params = new HttpParams()
      .set("query", query)
      .set("page", page)
      .set("size", 12);
    if (status) params = params.set("status", status);
    return this.get<AdminPage<AdminUser>>("users", params);
  }
  user(id: string) {
    return this.get<AdminUser>(`users/${id}`);
  }
  saveUser(user: AdminUser) {
    const { firstName, lastName, role, status, businessTier, version } = user;
    return this.http
      .put<
        ApiResponse<AdminUser>
      >(`${this.base}/admin/users/${user.id}`, { firstName, lastName, role, status, businessTier, version })
      .pipe(map((r) => r.data));
  }
  models() {
    return this.get<AdminPage<AdminModel>>(
      "models",
      new HttpParams().set("size", 50),
    );
  }
  saveModel(model: AdminModel) {
    return this.http
      .put<
        ApiResponse<AdminModel>
      >(`${this.base}/admin/models/${encodeURIComponent(model.id)}`, { visible: model.visible, version: model.version })
      .pipe(map((r) => r.data));
  }
  activity(page: number) {
    return this.get<AdminPage<AdminActivity>>(
      "activity",
      new HttpParams().set("page", page).set("size", 12),
    );
  }
  private get<T>(path: string, params = new HttpParams()) {
    return this.http
      .get<ApiResponse<T>>(`${this.base}/admin/${path}`, { params })
      .pipe(map((r) => r.data));
  }
}
