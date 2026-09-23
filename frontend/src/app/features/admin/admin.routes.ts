import { inject } from "@angular/core";
import { CanActivateFn, Router, Routes } from "@angular/router";
import { MatSnackBar } from "@angular/material/snack-bar";
import { catchError, map, of } from "rxjs";
import { AdminService } from "./admin.service";

export const adminGuard: CanActivateFn = () => {
  const router = inject(Router);
  const snack = inject(MatSnackBar);
  return inject(AdminService)
    .access()
    .pipe(
      map((access) => {
        if (access.administrator) return true;
        snack.open(
          "Administrator access is required to open the console.",
          "Dismiss",
          { duration: 6000 },
        );
        return router.parseUrl("/home");
      }),
      catchError(() => of(router.parseUrl("/home"))),
    );
};

const page = () => import("./admin.component").then((m) => m.AdminComponent);
export const ADMIN_ROUTES: Routes = [
  {
    path: "",
    pathMatch: "full",
    loadComponent: page,
    canActivate: [adminGuard],
    data: { view: "overview" },
  },
  {
    path: "users",
    loadComponent: page,
    canActivate: [adminGuard],
    data: { view: "users" },
  },
  {
    path: "users/:id",
    loadComponent: page,
    canActivate: [adminGuard],
    data: { view: "detail" },
  },
  {
    path: "ai",
    loadComponent: page,
    canActivate: [adminGuard],
    data: { view: "ai" },
  },
  {
    path: "activity",
    loadComponent: page,
    canActivate: [adminGuard],
    data: { view: "activity" },
  },
];
