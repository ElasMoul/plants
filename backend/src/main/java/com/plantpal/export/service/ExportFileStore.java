package com.plantpal.export.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Owns the export directory. A file is named {@code {userId}-{uuid}.zip}, so a lookup that always
 * starts from the authenticated user id can only ever reach that user's files.
 */
@Component
public class ExportFileStore {

  private static final Logger log = LoggerFactory.getLogger(ExportFileStore.class);

  private final Path directory;
  private final Duration retention;
  private final Clock clock;

  public ExportFileStore(
      @Value("${app.export.directory}") String directory,
      @Value("${app.export.retention-hours:24}") int retentionHours,
      Clock clock) {
    this.directory = Path.of(directory).toAbsolutePath().normalize();
    this.retention = Duration.ofHours(retentionHours);
    this.clock = clock;
  }

  public int retentionHours() {
    return (int) retention.toHours();
  }

  public Path newTempPath(Long userId) {
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return directory.resolve(userId + "-" + UUID.randomUUID() + ".partial");
  }

  /** Atomically publishes a finished temp file and returns its export id. */
  public String publish(Long userId, Path temp) throws IOException {
    String id = UUID.randomUUID().toString();
    Files.move(temp, fileFor(userId, id), StandardCopyOption.ATOMIC_MOVE);
    return id;
  }

  /** Empty for unparsable, missing, expired or foreign ids — callers cannot tell which. */
  public Optional<Path> resolve(Long userId, String id) {
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      log.debug("Rejected malformed export id: {}", e.getMessage());
      return Optional.empty();
    }
    Path file = fileFor(userId, uuid.toString());
    if (!Files.isRegularFile(file) || isExpired(file)) {
      return Optional.empty();
    }
    return Optional.of(file);
  }

  public Instant expiresAt(Path file) {
    try {
      return Files.getLastModifiedTime(file).toInstant().plus(retention);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Deletes every export (and abandoned partial) older than the retention period. */
  public int deleteExpired() {
    if (!Files.isDirectory(directory)) {
      return 0;
    }
    int deleted = 0;
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        if (Files.isRegularFile(file) && isExpired(file) && deleteQuietly(file)) {
          deleted++;
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return deleted;
  }

  private boolean deleteQuietly(Path file) {
    try {
      return Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not delete an expired export file", e);
      return false;
    }
  }

  private Path fileFor(Long userId, String id) {
    return directory.resolve(userId + "-" + id + ".zip");
  }

  private boolean isExpired(Path file) {
    return !clock.instant().isBefore(expiresAt(file));
  }
}
