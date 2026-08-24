package com.plantpal.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.AbstractIntegrationTest;
import com.plantpal.shared.storage.CloudinaryFileStorageService;
import com.plantpal.shared.storage.FileStorageService;
import com.plantpal.shared.storage.LocalFileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the full context with {@code app.storage.type=cloudinary} — the ONE configuration no other
 * test exercises (everything else runs storage.type=local). This exact gap let a
 * Spring-uninstantiable CloudinaryFileStorageService (two public constructors, "No default
 * constructor found") sail through a green CI and fail prod's healthcheck on 2026-08-25. The dummy
 * CLOUDINARY_URL only needs to parse; no Cloudinary call is made here.
 */
@TestPropertySource(
    properties = {
      "app.storage.type=cloudinary",
      "app.storage.cloudinary-url=cloudinary://key:secret@test-cloud"
    })
@DisplayName("Cloudinary storage wiring — Integration Test")
class CloudinaryStorageWiringIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext context;
  @Autowired private FileStorageService fileStorageService;

  @Test
  @DisplayName("storage.type=cloudinary boots the context with exactly the Cloudinary impl")
  void cloudinaryModeWiresTheCloudinaryImpl() {
    assertThat(fileStorageService).isInstanceOf(CloudinaryFileStorageService.class);
    assertThat(context.getBeanNamesForType(LocalFileStorageService.class)).isEmpty();
    assertThat(context.getBeanNamesForType(FileStorageService.class)).hasSize(1);
  }
}
