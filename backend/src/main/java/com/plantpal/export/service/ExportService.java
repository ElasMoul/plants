package com.plantpal.export.service;

import com.plantpal.export.dto.ExportResponse;
import java.nio.file.Path;

public interface ExportService {

  /** Builds the authenticated user's export ZIP; throws RateLimitException past the limit. */
  ExportResponse createExport(Long userId);

  /** The user's own, unexpired export file; ResourceNotFoundException otherwise. */
  Path getExportFile(Long userId, String exportId);
}
