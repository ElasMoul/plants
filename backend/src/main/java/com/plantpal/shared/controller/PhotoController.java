package com.plantpal.shared.controller;

import com.plantpal.shared.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Photos", description = "Serves uploaded plant photos (Redis-cached, disk fallback)")
public class PhotoController {

  private final FileStorageService fileStorageService;

  public PhotoController(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  @Operation(summary = "Get a previously uploaded photo by filename (public — no auth required)")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Photo bytes returned"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Photo not found")
  })
  // /photos/{filename} (the URL shape persisted on every Identification row) is ALSO mapped
  // here: controllers win over StorageConfig's disk-only resource handler, so every photo
  // read goes through the storage service (Redis cache → local disk or Cloudinary) instead
  // of 404ing when the ephemeral container disk was wiped by a redeploy.
  @GetMapping({"/api/v1/photos/{filename}", "/photos/{filename}"})
  public ResponseEntity<byte[]> getPhoto(
      @Parameter(description = "Stored photo filename") @PathVariable String filename) {
    byte[] bytes = fileStorageService.loadPhotoBytes("/photos/" + filename);
    return ResponseEntity.ok().contentType(resolveContentType(filename)).body(bytes);
  }

  private MediaType resolveContentType(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
    if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
    return MediaType.IMAGE_JPEG;
  }
}
