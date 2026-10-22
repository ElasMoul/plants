package com.plantpal.export.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExportCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(ExportCleanupJob.class);

  private final ExportFileStore fileStore;

  public ExportCleanupJob(ExportFileStore fileStore) {
    this.fileStore = fileStore;
  }

  @Scheduled(cron = "0 0 * * * *")
  public void deleteExpiredExports() {
    int deleted = fileStore.deleteExpired();
    if (deleted > 0) {
      log.info("Deleted {} expired data export file(s)", deleted);
    }
  }
}
