package com.plantpal.admin.controller;

import com.plantpal.admin.dto.AdminDtos.Activity;
import com.plantpal.admin.dto.AdminDtos.ModelUpdate;
import com.plantpal.admin.dto.AdminDtos.ModelView;
import com.plantpal.admin.dto.AdminDtos.Overview;
import com.plantpal.admin.dto.AdminDtos.UserUpdate;
import com.plantpal.admin.dto.AdminDtos.UserView;
import com.plantpal.admin.service.AdminService;
import com.plantpal.admin.service.ModelCatalogService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import com.plantpal.user.entity.UserStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminService admin;
  private final ModelCatalogService models;

  public AdminController(AdminService admin, ModelCatalogService models) {
    this.admin = admin;
    this.models = models;
  }

  @GetMapping("/overview")
  public ApiResponse<Overview> overview() {
    return ApiResponse.success(admin.overview());
  }

  @GetMapping("/users")
  public ApiResponse<Page<UserView>> users(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(required = false) UserStatus status,
      Pageable pageable) {
    return ApiResponse.success(admin.users(query, status, bounded(pageable, "createdAt")));
  }

  @GetMapping("/users/{id}")
  public ApiResponse<UserView> user(@PathVariable Long id) {
    return ApiResponse.success(admin.user(id));
  }

  @PutMapping("/users/{id}")
  public ApiResponse<UserView> updateUser(
      @PathVariable Long id,
      @Valid @RequestBody UserUpdate request,
      @AuthenticationPrincipal User actor) {
    return ApiResponse.success(admin.updateUser(id, request, actor.getId()));
  }

  @GetMapping("/models")
  public ApiResponse<Page<ModelView>> models(Pageable pageable) {
    return ApiResponse.success(models.list(bounded(pageable, "id")));
  }

  @PutMapping("/models/{id}")
  public ApiResponse<ModelView> updateModel(
      @PathVariable String id,
      @Valid @RequestBody ModelUpdate request,
      @AuthenticationPrincipal User actor) {
    return ApiResponse.success(models.update(id, request, actor.getId()));
  }

  @GetMapping("/activity")
  public ApiResponse<Page<Activity>> activity(Pageable pageable) {
    return ApiResponse.success(admin.activity(bounded(pageable, "createdAt")));
  }

  private Pageable bounded(Pageable page, String sort) {
    Sort order = Sort.by(sort).descending();
    if (!sort.equals("id")) order = order.and(Sort.by("id").descending());
    return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 100), order);
  }
}
