package com.plantpal.export.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.export.dto.ExportResponse;
import com.plantpal.export.service.CsvEscaper;
import com.plantpal.export.service.ExportFileStore;
import com.plantpal.export.service.ExportService;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.journal.entity.PlantJournalEvent;
import com.plantpal.journal.repository.PlantJournalEventRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.reminder.entity.CareLog;
import com.plantpal.reminder.repository.CareLogRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.RateLimitException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.ratelimit.BoundedBucketStore;
import com.plantpal.shared.storage.FileStorageService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ExportServiceImpl implements ExportService {

  private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);

  private static final String COL_PHOTO_URL = "photoUrl";
  private static final String COL_COMMON_NAME = "commonName";
  private static final String COL_PLANT_ID = "plantId";
  private static final String COL_NOTES = "notes";
  private static final String COL_STATUS = "status";
  private static final int PAGE_SIZE = 200;
  private static final int MAX_TRACKED_USERS = 10_000;
  private static final String NOT_FOUND = "Export not found";
  private static final String PHOTO_URL_PREFIX = "/photos/";
  private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,5}");

  private final PlantRepository plantRepository;
  private final CareLogRepository careLogRepository;
  private final IdentificationRepository identificationRepository;
  private final PlantJournalEventRepository journalRepository;
  private final FileStorageService fileStorageService;
  private final ExportFileStore fileStore;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final BoundedBucketStore buckets;

  @Value("${app.export.rate-limit-per-hour:3}")
  private int rateLimitPerHour;

  @Value("${app.export.max-photos:200}")
  private int maxPhotos;

  @Value("${app.export.max-photo-bytes:209715200}")
  private long maxPhotoBytes;

  public ExportServiceImpl(
      PlantRepository plantRepository,
      CareLogRepository careLogRepository,
      IdentificationRepository identificationRepository,
      PlantJournalEventRepository journalRepository,
      FileStorageService fileStorageService,
      ExportFileStore fileStore,
      ObjectMapper objectMapper,
      Clock clock) {
    this.plantRepository = plantRepository;
    this.careLogRepository = careLogRepository;
    this.identificationRepository = identificationRepository;
    this.journalRepository = journalRepository;
    this.fileStorageService = fileStorageService;
    this.fileStore = fileStore;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.buckets = new BoundedBucketStore(MAX_TRACKED_USERS);
  }

  @Override
  public ExportResponse createExport(Long userId) {
    consumeRateLimit(userId);
    Path temp = fileStore.newTempPath(userId);
    try {
      writeArchive(userId, temp);
      String id = fileStore.publish(userId, temp);
      Path file = fileStore.resolve(userId, id).orElseThrow();
      log.info("Data export created for user {}", userId);
      return new ExportResponse(
          id, fileStore.expiresAt(file), Files.size(file), fileStore.retentionHours());
    } catch (IOException e) {
      log.warn("Export build failed for user {}", userId, e);
      deleteQuietly(temp);
      throw new PlantPalException("Could not build the export", 500);
    } catch (RuntimeException e) {
      deleteQuietly(temp);
      throw e;
    }
  }

  @Override
  public Path getExportFile(Long userId, String exportId) {
    return fileStore
        .resolve(userId, exportId)
        .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
  }

  private void writeArchive(Long userId, Path target) throws IOException {
    List<String> skippedPhotos = new ArrayList<>();
    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target));
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      writeDataset(
          zip,
          "plants",
          List.of(
              "id",
              "nickname",
              "species",
              COL_COMMON_NAME,
              "location",
              COL_NOTES,
              COL_STATUS,
              "acquiredAt",
              "placement",
              "lightExposure",
              COL_PHOTO_URL,
              "createdAt"),
          p -> plantRepository.findByUserIdOrderByIdAsc(userId, p).map(this::plantRow));
      writeDataset(
          zip,
          "care-log",
          List.of("id", COL_PLANT_ID, "careType", COL_NOTES, "performedAt"),
          p -> careLogRepository.findByUserIdOrderByIdAsc(userId, p).map(this::careLogRow));
      writeDataset(
          zip,
          "scan-history",
          List.of(
              "id",
              COL_PLANT_ID,
              "dateTaken",
              "scientificName",
              COL_COMMON_NAME,
              "confidence",
              COL_STATUS,
              "healthStatus",
              "healthNotes",
              "carePlan",
              "aiModelUsed",
              "userContext",
              COL_PHOTO_URL),
          p -> identificationRepository.findByUserIdOrderByIdAsc(userId, p).map(this::scanRow));
      writeDataset(
          zip,
          "journal",
          List.of("id", COL_PLANT_ID, "type", "eventDate", COL_NOTES),
          p -> journalRepository.findByUserIdOrderByIdAsc(userId, p).map(this::journalRow));
      writePhotos(zip, userId, skippedPhotos);
      writeReadme(zip, skippedPhotos);
    }
  }

  private void writeDataset(
      ZipOutputStream zip,
      String name,
      List<String> columns,
      Function<Pageable, Page<Map<String, Object>>> pager)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name + ".json"));
    try (JsonGenerator json = objectMapper.getFactory().createGenerator(nonClosing(zip))) {
      json.writeStartArray();
      forEachRow(pager, row -> writeJsonRow(json, row));
      json.writeEndArray();
    }
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry(name + ".csv"));
    zip.write(CsvEscaper.row(columns).getBytes(StandardCharsets.UTF_8));
    forEachRow(
        pager,
        row -> {
          List<Object> values = new ArrayList<>();
          columns.forEach(c -> values.add(row.get(c)));
          writeBytes(zip, CsvEscaper.row(values));
        });
    zip.closeEntry();
  }

  private void forEachRow(
      Function<Pageable, Page<Map<String, Object>>> pager,
      java.util.function.Consumer<Map<String, Object>> consumer) {
    Page<Map<String, Object>> page;
    int index = 0;
    do {
      page = pager.apply(PageRequest.of(index++, PAGE_SIZE));
      page.forEach(consumer);
    } while (page.hasNext());
  }

  private void writePhotos(ZipOutputStream zip, Long userId, List<String> skipped)
      throws IOException {
    Map<String, String> entryByUrl = new LinkedHashMap<>();
    forEachRow(
        p -> plantRepository.findByUserIdOrderByIdAsc(userId, p).map(this::plantRow),
        row -> addPhotoRef(entryByUrl, "plant-" + row.get("id"), row.get(COL_PHOTO_URL)));
    forEachRow(
        p -> identificationRepository.findByUserIdOrderByIdAsc(userId, p).map(this::scanRow),
        row -> addPhotoRef(entryByUrl, "scan-" + row.get("id"), row.get(COL_PHOTO_URL)));

    int written = 0;
    long totalBytes = 0;
    for (Map.Entry<String, String> photo : entryByUrl.entrySet()) {
      String entryName = photo.getValue();
      if (written >= maxPhotos) {
        skipped.add(entryName + " (photo limit reached)");
        continue;
      }
      byte[] bytes = readPhoto(photo.getKey()).orElse(null);
      if (bytes == null) {
        skipped.add(entryName + " (could not be read)");
      } else if (totalBytes + bytes.length > maxPhotoBytes) {
        skipped.add(entryName + " (size limit reached)");
      } else {
        zip.putNextEntry(new ZipEntry("photos/" + entryName));
        zip.write(bytes);
        zip.closeEntry();
        written++;
        totalBytes += bytes.length;
      }
    }
  }

  private void addPhotoRef(Map<String, String> entryByUrl, String baseName, Object url) {
    if (url instanceof String photoUrl
        && photoUrl.startsWith(PHOTO_URL_PREFIX)
        && !entryByUrl.containsKey(photoUrl)) {
      entryByUrl.put(photoUrl, baseName + "." + extensionOf(photoUrl));
    }
  }

  private String extensionOf(String url) {
    int dot = url.lastIndexOf('.');
    String ext = dot >= 0 ? url.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    return SAFE_EXTENSION.matcher(ext).matches() ? ext : "jpg";
  }

  private Optional<byte[]> readPhoto(String url) {
    try {
      return Optional.ofNullable(fileStorageService.loadPhotoBytes(url));
    } catch (RuntimeException e) {
      log.debug("Photo could not be read for export", e);
      return Optional.empty();
    }
  }

  private void writeReadme(ZipOutputStream zip, List<String> skippedPhotos) throws IOException {
    StringBuilder text = new StringBuilder();
    text.append("PlantPal data export\r\n")
        .append("Created: ")
        .append(clock.instant())
        .append("\r\n")
        .append("This file is deleted from our servers ")
        .append(fileStore.retentionHours())
        .append(" hours after it was created.\r\n\r\n")
        .append(
            "Contents: plants, care-log, scan-history and journal (JSON and CSV) plus photos/.\r\n")
        .append("Archived plants are included.\r\n");
    if (!skippedPhotos.isEmpty()) {
      text.append("\r\nPhotos not included:\r\n");
      skippedPhotos.forEach(s -> text.append(" - ").append(s).append("\r\n"));
    }
    zip.putNextEntry(new ZipEntry("README.txt"));
    writeBytes(zip, text.toString());
    zip.closeEntry();
  }

  private Map<String, Object> plantRow(Plant p) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", p.getId());
    row.put("nickname", p.getNickname());
    row.put("species", p.getSpecies());
    row.put(COL_COMMON_NAME, p.getCommonName());
    row.put("location", p.getLocation());
    row.put(COL_NOTES, p.getNotes());
    row.put(COL_STATUS, str(p.getStatus()));
    row.put("acquiredAt", str(p.getAcquiredAt()));
    row.put("placement", str(p.getPlacement()));
    row.put("lightExposure", str(p.getLightExposure()));
    row.put(COL_PHOTO_URL, p.getPhotoUrl());
    row.put("createdAt", str(p.getCreatedAt()));
    return row;
  }

  private Map<String, Object> careLogRow(CareLog c) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", c.getId());
    row.put(COL_PLANT_ID, c.getPlantId());
    row.put("careType", str(c.getCareType()));
    row.put(COL_NOTES, c.getNotes());
    row.put("performedAt", str(c.getPerformedAt()));
    return row;
  }

  private Map<String, Object> journalRow(PlantJournalEvent e) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", e.getId());
    row.put(COL_PLANT_ID, e.getPlantId());
    row.put("type", str(e.getType()));
    row.put("eventDate", str(e.getEventDate()));
    row.put(COL_NOTES, e.getNote());
    return row;
  }

  private Map<String, Object> scanRow(Identification i) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", i.getId());
    row.put(COL_PLANT_ID, i.getPlantId());
    row.put("dateTaken", str(i.getDateTaken()));
    row.put("scientificName", i.getScientificName());
    row.put(COL_COMMON_NAME, i.getCommonName());
    row.put("confidence", i.getConfidence());
    row.put(COL_STATUS, str(i.getStatus()));
    row.put("healthStatus", i.getHealthStatus());
    row.put("healthNotes", i.getHealthNotes());
    row.put("carePlan", i.getCarePlan());
    row.put("aiModelUsed", i.getAiModelUsed());
    row.put("userContext", i.getUserContext());
    row.put(COL_PHOTO_URL, i.getPhotoUrl());
    return row;
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }

  private void writeJsonRow(JsonGenerator json, Map<String, Object> row) {
    try {
      objectMapper.writeValue(json, row);
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static void writeBytes(OutputStream out, String text) {
    try {
      out.write(text.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** Jackson must not close the ZIP stream when it finishes one entry. */
  private static OutputStream nonClosing(OutputStream out) {
    return new java.io.FilterOutputStream(out) {
      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
      }

      @Override
      public void close() throws IOException {
        flush();
      }
    };
  }

  private void consumeRateLimit(Long userId) {
    Bucket bucket =
        buckets.resolveBucket(
            String.valueOf(userId),
            () ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(rateLimitPerHour)
                            .refillIntervally(rateLimitPerHour, Duration.ofHours(1))
                            .build())
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long wait = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
      throw new RateLimitException("Too many export requests — try again later", wait);
    }
  }

  private void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not remove a failed export file", e);
    }
  }
}
