package com.plantpal.admin.controller;

import com.plantpal.admin.service.AdminPlantService;
import com.plantpal.plant.dto.PlantResponse;
import com.plantpal.plant.dto.UpdatePlantRequest;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}/plants")
public class AdminPlantController {
  private final AdminPlantService service;

  public AdminPlantController(AdminPlantService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<Page<PlantResponse>> list(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "ACTIVE") PlantStatus status,
      Pageable page) {
    return ApiResponse.success(
        service.list(
            userId,
            status,
            PageRequest.of(
                page.getPageNumber(),
                Math.min(page.getPageSize(), 100),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()))));
  }

  @GetMapping("/{plantId}")
  public ApiResponse<com.plantpal.admin.dto.AdminDtos.PlantDetail> view(
      @PathVariable Long userId, @PathVariable Long plantId, Pageable page) {
    return ApiResponse.success(
        service.view(
            userId,
            plantId,
            PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 50))));
  }

  @PutMapping("/{plantId}")
  public ApiResponse<PlantResponse> update(
      @PathVariable Long userId,
      @PathVariable Long plantId,
      @Valid @RequestBody UpdatePlantRequest request,
      @AuthenticationPrincipal User actor) {
    return ApiResponse.success(service.update(userId, plantId, request, actor.getId()));
  }

  @DeleteMapping("/{plantId}")
  public ApiResponse<String> archive(
      @PathVariable Long userId, @PathVariable Long plantId, @AuthenticationPrincipal User actor) {
    service.archive(userId, plantId, actor.getId());
    return ApiResponse.success("Plant archived");
  }

  @PostMapping("/{plantId}/restore")
  public ApiResponse<PlantResponse> restore(
      @PathVariable Long userId, @PathVariable Long plantId, @AuthenticationPrincipal User actor) {
    return ApiResponse.success(service.restore(userId, plantId, actor.getId()));
  }
}
