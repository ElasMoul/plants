package com.plantpal.identification.migration;

import com.plantpal.identification.util.PhotoGpsStripper;
import com.plantpal.shared.storage.FileStorageService;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rewrites already-stored photos through {@link PhotoGpsStripper} (PLA-118). Exposed statically so
 * the Liquibase custom task, which Liquibase instantiates itself, can reach the Spring-managed
 * storage; {@link PhotoGpsMigrationConfig} makes Liquibase start after this bean.
 */
@Component
public class PhotoGpsCleaner {

  private static final Logger log = LoggerFactory.getLogger(PhotoGpsCleaner.class);

  private static final AtomicReference<PhotoGpsCleaner> INSTANCE = new AtomicReference<>();

  private final FileStorageService fileStorageService;

  public PhotoGpsCleaner(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  @PostConstruct
  void register() {
    INSTANCE.set(this);
  }

  public static PhotoGpsCleaner current() {
    return INSTANCE.get();
  }

  /** Counts for one run. */
  public record Summary(int scanned, int rewritten, int unchanged, int failed) {}

  public Summary cleanAll(Collection<String> photoUrls) {
    int rewritten = 0;
    int unchanged = 0;
    int failed = 0;
    for (String url : photoUrls) {
      try {
        byte[] original = fileStorageService.loadPhotoBytes(url);
        byte[] stripped = PhotoGpsStripper.strip(original);
        if (stripped == original) {
          unchanged++;
        } else {
          fileStorageService.overwritePhoto(url, stripped);
          rewritten++;
        }
      } catch (RuntimeException e) {
        failed++;
        log.warn("Photo GPS cleanup skipped {}: {}", url, e.getMessage());
      }
    }
    Summary summary = new Summary(photoUrls.size(), rewritten, unchanged, failed);
    log.info(
        "Photo GPS cleanup: scanned={}, rewritten={}, unchanged={}, failed={}",
        summary.scanned(),
        summary.rewritten(),
        summary.unchanged(),
        summary.failed());
    return summary;
  }
}
