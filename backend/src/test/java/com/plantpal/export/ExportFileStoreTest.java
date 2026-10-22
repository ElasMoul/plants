package com.plantpal.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.export.service.ExportFileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ExportFileStore - Unit Tests")
class ExportFileStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

  @TempDir Path dir;

  private ExportFileStore store() {
    return new ExportFileStore(dir.toString(), 24, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private String publish(ExportFileStore store, long userId, Instant modified) throws Exception {
    Path temp = store.newTempPath(userId);
    Files.writeString(temp, "zip");
    String id = store.publish(userId, temp);
    Files.setLastModifiedTime(dir.resolve(userId + "-" + id + ".zip"), FileTime.from(modified));
    return id;
  }

  @Test
  @DisplayName("deletes files past retention, keeps fresh ones, and refuses expired downloads")
  void cleanupHonoursRetention() throws Exception {
    ExportFileStore store = store();
    String old = publish(store, 1L, NOW.minus(Duration.ofHours(25)));
    String fresh = publish(store, 1L, NOW.minus(Duration.ofHours(23)));

    assertThat(store.resolve(1L, old)).isEmpty(); // refused even before cleanup runs
    assertThat(store.deleteExpired()).isEqualTo(1);

    assertThat(dir.resolve("1-" + old + ".zip")).doesNotExist();
    assertThat(store.resolve(1L, fresh)).isPresent();
  }

  @Test
  @DisplayName("cleanup also removes abandoned partial files")
  void cleanupRemovesPartials() throws Exception {
    ExportFileStore store = store();
    Path partial = store.newTempPath(1L);
    Files.writeString(partial, "x");
    Files.setLastModifiedTime(partial, FileTime.from(NOW.minus(Duration.ofHours(30))));

    assertThat(store.deleteExpired()).isEqualTo(1);
    assertThat(partial).doesNotExist();
  }

  @Test
  @DisplayName("resolve only reaches the owner's file and rejects non-UUID ids")
  void resolveIsOwnerScoped() throws Exception {
    ExportFileStore store = store();
    String id = publish(store, 1L, NOW);

    assertThat(store.resolve(1L, id)).isPresent();
    assertThat(store.resolve(2L, id)).isEmpty();
    assertThat(store.resolve(1L, "../1-" + id)).isEmpty();
    assertThat(store.resolve(1L, UUID.randomUUID().toString())).isEmpty();
  }
}
