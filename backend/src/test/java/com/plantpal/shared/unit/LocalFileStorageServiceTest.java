package com.plantpal.shared.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.storage.LocalFileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalFileStorageService — Unit Tests")
class LocalFileStorageServiceTest {

  @Mock private RedisTemplate<String, byte[]> byteRedisTemplate;
  @Mock private ValueOperations<String, byte[]> byteValueOps;
  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> stringValueOps;

  @TempDir private Path tempDir;

  private LocalFileStorageService fileStorageService;

  @BeforeEach
  void setUp() {
    fileStorageService =
        new LocalFileStorageService(tempDir.toString(), byteRedisTemplate, stringRedisTemplate);
  }

  private MockMultipartFile photo(byte[] bytes) {
    return new MockMultipartFile("image", "plant.jpg", "image/jpeg", bytes);
  }

  @Nested
  @DisplayName("savePhoto()")
  class SavePhoto {

    @Test
    @DisplayName("first upload stores bytes + hash mapping in Redis and returns a new URL")
    void shouldStoreBytesAndHashOnFirstUpload() {
      when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOps);
      when(stringValueOps.get(anyString())).thenReturn(null);
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      byte[] bytes = {1, 2, 3, 4};

      String url = fileStorageService.savePhoto(photo(bytes));

      assertThat(url).startsWith("/photos/").endsWith(".jpg");
      verify(byteValueOps).set(anyString(), eq(bytes), eq(Duration.ofDays(30)));
      verify(stringValueOps).set(anyString(), eq(url), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("re-uploading identical bytes returns the same URL without writing to disk again")
    void shouldDedupOnSecondUpload() {
      when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOps);
      String existingUrl = "/photos/existing-uuid.jpg";
      when(stringValueOps.get(anyString())).thenReturn(existingUrl);

      String url = fileStorageService.savePhoto(photo(new byte[] {1, 2, 3, 4}));

      assertThat(url).isEqualTo(existingUrl);
      verify(byteRedisTemplate, never()).opsForValue();
    }
  }

  @Nested
  @DisplayName("loadPhotoBytes()")
  class LoadPhotoBytes {

    @Test
    @DisplayName("returns bytes from Redis without touching disk when cached")
    void shouldReturnFromRedisOnHit() {
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      byte[] cached = {9, 9, 9};
      when(byteValueOps.get("photo:cached-uuid")).thenReturn(cached);

      byte[] result = fileStorageService.loadPhotoBytes("/photos/cached-uuid.jpg");

      assertThat(result).isEqualTo(cached);
    }

    @Test
    @DisplayName("falls back to disk when not cached in Redis")
    void shouldFallBackToDiskOnMiss() throws IOException {
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      when(byteValueOps.get(anyString())).thenReturn(null);
      Path file = tempDir.resolve("disk-uuid.jpg");
      Files.write(file, new byte[] {5, 6, 7});

      byte[] result = fileStorageService.loadPhotoBytes("/photos/disk-uuid.jpg");

      assertThat(result).containsExactly(5, 6, 7);
    }

    @Test
    @DisplayName("throws ResourceNotFoundException when neither Redis nor disk has the photo")
    void shouldThrowWhenNotFoundAnywhere() {
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      when(byteValueOps.get(anyString())).thenReturn(null);

      assertThatThrownBy(() -> fileStorageService.loadPhotoBytes("/photos/missing-uuid.jpg"))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }
}
