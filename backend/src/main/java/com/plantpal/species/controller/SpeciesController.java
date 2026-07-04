package com.plantpal.species.controller;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.species.dto.SpeciesResponse;
import com.plantpal.species.dto.SpeciesSummaryDto;
import com.plantpal.species.service.SpeciesService;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Species rows are only ever created via {@code SpeciesService.findOrCreate()} from the
 * identification flow (T6.9) — no POST/PUT/DELETE here.
 */
@RestController
@RequestMapping("/api/v1/species")
@Tag(name = "Species", description = "Shared botanical species data")
@SecurityRequirement(name = "bearerAuth")
public class SpeciesController {

  private final SpeciesService speciesService;

  public SpeciesController(SpeciesService speciesService) {
    this.speciesService = speciesService;
  }

  @Operation(summary = "Get a species by ID (public read, no ownership check)")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<SpeciesResponse>> getSpecies(@PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(speciesService.getSpecies(id)));
  }

  @Operation(summary = "List the distinct species the current user owns at least one plant of")
  @GetMapping("/mine")
  public ResponseEntity<ApiResponse<Page<SpeciesSummaryDto>>> getMySpecies(
      @PageableDefault(size = 20) Pageable pageable) {
    Long userId = getCurrentUserId();
    Page<SpeciesSummaryDto> species = speciesService.getUserSpecies(userId, pageable);
    return ResponseEntity.ok(ApiResponse.success(species));
  }

  @Operation(summary = "Re-fire async prose description generation (T9.B)")
  @PostMapping("/{id}/regenerate-description")
  public ResponseEntity<ApiResponse<SpeciesResponse>> regenerateDescription(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    return ResponseEntity.accepted()
        .body(ApiResponse.success(speciesService.regenerateDescription(id, userId)));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
