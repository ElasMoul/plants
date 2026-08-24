package com.plantpal.shared.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.plantpal.shared.storage.CloudinaryFileStorageService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryFileStorageService — Unit Tests")
class CloudinaryFileStorageServiceTest {

  @Mock private Cloudinary cloudinary;
  @Mock private Uploader uploader;
  @Mock private RestClient deliveryClient;
  @Mock private RedisTemplate<String, byte[]> byteRedisTemplate;
  @Mock private ValueOperations<String, byte[]> byteValueOps;
  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> stringValueOps;

  private CloudinaryFileStorageService fileStorageService;

  @BeforeEach
  void setUp() {
    fileStorageService =
        new CloudinaryFileStorageService(
            cloudinary, deliveryClient, byteRedisTemplate, stringRedisTemplate);
  }

  private MockMultipartFile photo(byte[] bytes) {
    return new MockMultipartFile("image", "plant.jpg", "image/jpeg", bytes);
  }

  @Nested
  @DisplayName("savePhoto()")
  class SavePhoto {

    @Test
    @DisplayName("first upload sends bytes to Cloudinary, caches in Redis, returns /photos URL")
    void shouldUploadAndCacheOnFirstUpload() throws Exception {
      when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOps);
      when(stringValueOps.get(anyString())).thenReturn(null);
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      when(cloudinary.uploader()).thenReturn(uploader);
      when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("format", "jpg"));
      byte[] bytes = {1, 2, 3, 4};

      String url = fileStorageService.savePhoto(photo(bytes));

      assertThat(url).startsWith("/photos/").endsWith(".jpg");
      verify(uploader).upload(eq(bytes), anyMap());
      verify(byteValueOps).set(anyString(), eq(bytes), eq(Duration.ofDays(30)));
      verify(stringValueOps).set(anyString(), eq(url), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("trusts Cloudinary's normalized format over the client's claimed extension")
    void shouldUseCloudinaryFormatInUrl() throws Exception {
      when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOps);
      when(stringValueOps.get(anyString())).thenReturn(null);
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      when(cloudinary.uploader()).thenReturn(uploader);
      when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("format", "webp"));

      String url = fileStorageService.savePhoto(photo(new byte[] {9, 9}));

      assertThat(url).endsWith(".webp");
    }

    @Test
    @DisplayName("re-uploading identical bytes dedups via Redis without hitting Cloudinary")
    void shouldDedupOnSecondUpload() throws Exception {
      when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOps);
      String existingUrl = "/photos/existing-uuid.jpg";
      when(stringValueOps.get(anyString())).thenReturn(existingUrl);

      String url = fileStorageService.savePhoto(photo(new byte[] {1, 2, 3, 4}));

      assertThat(url).isEqualTo(existingUrl);
      verify(uploader, never()).upload(any(byte[].class), anyMap());
    }
  }

  @Nested
  @DisplayName("loadPhotoBytes()")
  class LoadPhotoBytes {

    @Test
    @DisplayName("returns Redis-cached bytes without calling Cloudinary")
    void shouldReturnCachedBytes() {
      byte[] cached = {5, 6, 7};
      when(byteRedisTemplate.opsForValue()).thenReturn(byteValueOps);
      when(byteValueOps.get("photo:some-uuid")).thenReturn(cached);

      byte[] result = fileStorageService.loadPhotoBytes("/photos/some-uuid.jpg");

      assertThat(result).isEqualTo(cached);
    }
  }

  @Nested
  @DisplayName("deletePhoto()")
  class DeletePhoto {

    @Test
    @DisplayName("destroys the Cloudinary asset and evicts the Redis byte cache")
    void shouldDestroyAndEvict() throws Exception {
      when(cloudinary.uploader()).thenReturn(uploader);
      when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

      fileStorageService.deletePhoto("/photos/some-uuid.jpg");

      verify(uploader).destroy(eq("plantpal/some-uuid"), anyMap());
      verify(byteRedisTemplate).delete("photo:some-uuid");
    }

    @Test
    @DisplayName("ignores URLs outside the /photos/ contract")
    void shouldIgnoreForeignUrls() throws Exception {
      fileStorageService.deletePhoto("https://elsewhere.example/img.jpg");
      verify(uploader, never()).destroy(anyString(), anyMap());
    }
  }
}
