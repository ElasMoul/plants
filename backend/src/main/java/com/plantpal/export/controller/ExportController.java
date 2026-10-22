package com.plantpal.export.controller;

import com.plantpal.export.dto.ExportResponse;
import com.plantpal.export.service.ExportService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/export")
@Tag(name = "Data export", description = "Self-service export of the user's own data")
@SecurityRequirement(name = "bearerAuth")
public class ExportController {

  private final ExportService exportService;

  public ExportController(ExportService exportService) {
    this.exportService = exportService;
  }

  @Operation(summary = "Build a ZIP of the current user's plants, history and photos")
  @PostMapping
  public ResponseEntity<ApiResponse<ExportResponse>> createExport(
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(ApiResponse.success(exportService.createExport(user.getId())));
  }

  @Operation(summary = "Download one of the current user's export ZIPs")
  @GetMapping("/{id}")
  public ResponseEntity<Resource> download(
      @AuthenticationPrincipal User user, @PathVariable String id) {
    Resource file = new FileSystemResource(exportService.getExportFile(user.getId(), id));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("plantpal-export.zip").build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file);
  }
}
