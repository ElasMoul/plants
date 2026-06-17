package com.plantpal.shared.controller;

import com.plantpal.shared.storage.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
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

  @Operation(summary = "Get a previously uploaded photo by filename")
  @GetMapping("/api/v1/photos/{filename}")
  public ResponseEntity<byte[]> getPhoto(@PathVariable String filename) {
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
