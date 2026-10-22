package com.plantpal.export.dto;

import java.time.Instant;

/** Result of an export request: the id to download with, when it expires, and its size. */
public record ExportResponse(String id, Instant expiresAt, long sizeBytes, int retentionHours) {}
