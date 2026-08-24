package com.plantpal.shared.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Durable photo store for production ({@code app.storage.type=cloudinary}) — the container
 * filesystem on Railway is wiped every deploy, so photos live in Cloudinary instead. The URL
 * contract is IDENTICAL to {@link LocalFileStorageService} ({@code /photos/{uuid}.{ext}}), and the
 * same Redis byte-cache/dedup keys are used, so nothing downstream (DB rows, frontend, the
 * identification pipeline's {@code loadPhotoBytes}) can tell the difference. Requires
 * CLOUDINARY_URL ({@code cloudinary://api_key:api_secret@cloud_name}); the bean fails fast at
 * startup when it's missing rather than 500ing on the first upload.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cloudinary")
public class CloudinaryFileStorageService implements FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(CloudinaryFileStorageService.class);

  private static final Duration PHOTO_TTL = Duration.ofDays(30);
  private static final String PHOTO_KEY_PREFIX = "photo:";
  private static final String PHOTO_HASH_KEY_PREFIX = "photo:hash:";
  private static final String PHOTOS_FOLDER = "plantpal";

  private final Cloudinary cloudinary;
  private final RestClient deliveryClient;
  private final RedisTemplate<String, byte[]> byteRedisTemplate;
  private final StringRedisTemplate stringRedisTemplate;

  public CloudinaryFileStorageService(
      @Value("${app.storage.cloudinary-url:${CLOUDINARY_URL:}}") String cloudinaryUrl,
      RedisTemplate<String, byte[]> byteRedisTemplate,
      StringRedisTemplate stringRedisTemplate) {
    if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
      throw new IllegalStateException(
          "app.storage.type=cloudinary but CLOUDINARY_URL is not set "
              + "(expected cloudinary://api_key:api_secret@cloud_name)");
    }
    this.cloudinary = new Cloudinary(cloudinaryUrl);
    this.deliveryClient =
        RestClient.builder()
            .baseUrl("https://res.cloudinary.com/" + cloudinary.config.cloudName + "/image/upload/")
            .build();
    this.byteRedisTemplate = byteRedisTemplate;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /**
   * Test seam — lets unit tests (in the shared.unit package, hence public) inject a mocked SDK
   * client and delivery client. Never wired by Spring: only the @Value constructor above is.
   */
  public CloudinaryFileStorageService(
      Cloudinary cloudinary,
      RestClient deliveryClient,
      RedisTemplate<String, byte[]> byteRedisTemplate,
      StringRedisTemplate stringRedisTemplate) {
    this.cloudinary = cloudinary;
    this.deliveryClient = deliveryClient;
    this.byteRedisTemplate = byteRedisTemplate;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public String savePhoto(MultipartFile file) {
    try {
      byte[] fileBytes = file.getBytes();
      String hash = DigestUtils.sha256Hex(fileBytes);

      String existingUrl = stringRedisTemplate.opsForValue().get(PHOTO_HASH_KEY_PREFIX + hash);
      if (existingUrl != null) {
        log.info("Photo dedup hit [hash={}]", hash);
        return existingUrl;
      }

      String uuid = UUID.randomUUID().toString();
      Map<?, ?> result =
          cloudinary
              .uploader()
              .upload(
                  fileBytes,
                  ObjectUtils.asMap(
                      "public_id", PHOTOS_FOLDER + "/" + uuid, "resource_type", "image"));
      // Cloudinary normalizes the format server-side — trust its answer over the client's
      // claimed extension so the delivery URL below always resolves.
      String format = result.get("format") != null ? result.get("format").toString() : "jpg";
      log.debug("Uploaded photo to Cloudinary: {}/{}.{}", PHOTOS_FOLDER, uuid, format);

      String url = "/photos/" + uuid + "." + format;
      byteRedisTemplate.opsForValue().set(PHOTO_KEY_PREFIX + uuid, fileBytes, PHOTO_TTL);
      stringRedisTemplate.opsForValue().set(PHOTO_HASH_KEY_PREFIX + hash, url, PHOTO_TTL);
      return url;
    } catch (IOException e) {
      log.error("Failed to save photo to Cloudinary", e);
      throw new PlantPalException("Failed to save photo", 500);
    }
  }

  @Override
  public byte[] loadPhotoBytes(String photoUrl) {
    String filename = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
    String uuid =
        filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;

    byte[] cached = byteRedisTemplate.opsForValue().get(PHOTO_KEY_PREFIX + uuid);
    if (cached != null) {
      return cached;
    }

    try {
      byte[] bytes =
          deliveryClient.get().uri(PHOTOS_FOLDER + "/" + filename).retrieve().body(byte[].class);
      if (bytes == null || bytes.length == 0) {
        throw new ResourceNotFoundException("Photo not found: " + photoUrl);
      }
      byteRedisTemplate.opsForValue().set(PHOTO_KEY_PREFIX + uuid, bytes, PHOTO_TTL);
      return bytes;
    } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
      throw new ResourceNotFoundException("Photo not found: " + photoUrl);
    } catch (org.springframework.web.client.RestClientException e) {
      log.error("Failed to load photo from Cloudinary: {}", filename, e);
      throw new PlantPalException("Failed to load photo", 500);
    }
  }

  @Override
  public void deletePhoto(String url) {
    if (url == null || !url.startsWith("/photos/")) return;
    String filename = url.substring("/photos/".length());
    String uuid =
        filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
    try {
      cloudinary
          .uploader()
          .destroy(PHOTOS_FOLDER + "/" + uuid, ObjectUtils.asMap("invalidate", true));
      byteRedisTemplate.delete(PHOTO_KEY_PREFIX + uuid);
      log.debug("Deleted photo from Cloudinary: {}/{}", PHOTOS_FOLDER, uuid);
    } catch (IOException e) {
      log.warn("Failed to delete photo from Cloudinary: {}", filename, e);
    }
  }
}
