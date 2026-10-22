package com.plantpal.export;

import static org.mockito.Mockito.when;

import com.plantpal.export.service.ExportCleanupJob;
import com.plantpal.export.service.ExportFileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportCleanupJob - Unit Tests")
class ExportCleanupJobTest {

  @Mock private ExportFileStore fileStore;

  @Test
  @DisplayName("delegates to the file store whether or not anything was deleted")
  void delegates() {
    when(fileStore.deleteExpired()).thenReturn(2, 0);
    ExportCleanupJob job = new ExportCleanupJob(fileStore);

    job.deleteExpiredExports();
    job.deleteExpiredExports();

    org.mockito.Mockito.verify(fileStore, org.mockito.Mockito.times(2)).deleteExpired();
  }
}
