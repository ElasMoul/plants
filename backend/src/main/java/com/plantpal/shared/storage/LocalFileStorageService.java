package com.plantpal.shared.storage;

import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!prod")
public class LocalFileStorageService implements FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

  private static final Duration PHOTO_TTL = Duration.ofDays(7);
  private static final String PHOTO_KEY_PREFIX = "photo:";
  private static final String PHOTO_HASH_KEY_PREFIX = "photo:hash:";

  private final Path storageRoot;
  private final RedisTemplate<String, byte[]> byteRedisTemplate;
  private final StringRedisTemplate stringRedisTemplate;

  public LocalFileStorageService(
      @Value("${app.storage.local-path:/tmp/plantpal/photos}") String localPath,
      RedisTemplate<String, byte[]> byteRedisTemplate,
      StringRedisTemplate stringRedisTemplate) {
    this.storageRoot = Paths.get(localPath);
    this.byteRedisTemplate = byteRedisTemplate;
    this.stringRedisTemplate = stringRedisTemplate;
    try {
      Files.createDirectories(storageRoot);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create storage directory: " + localPath, e);
    }
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

      String extension = resolveExtension(file.getOriginalFilename());
      String uuid = UUID.randomUUID().toString();
      String filename = uuid + extension;
      Path target = storageRoot.resolve(filename);
      Files.write(target, fileBytes);
      log.debug("Saved photo: {}", target);

      String url = "/photos/" + filename;
      byteRedisTemplate.opsForValue().set(PHOTO_KEY_PREFIX + uuid, fileBytes, PHOTO_TTL);
      stringRedisTemplate.opsForValue().set(PHOTO_HASH_KEY_PREFIX + hash, url, PHOTO_TTL);
      return url;
    } catch (IOException e) {
      log.error("Failed to save photo", e);
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

    Path target = storageRoot.resolve(filename);
    if (!Files.exists(target)) {
      throw new ResourceNotFoundException("Photo not found: " + photoUrl);
    }
    try {
      return Files.readAllBytes(target);
    } catch (IOException e) {
      log.error("Failed to load photo: {}", filename, e);
      throw new PlantPalException("Failed to load photo", 500);
    }
  }

  @Override
  public void deletePhoto(String url) {
    if (url == null || !url.startsWith("/photos/")) return;
    String filename = url.substring("/photos/".length());
    Path target = storageRoot.resolve(filename);
    try {
      Files.deleteIfExists(target);
      log.debug("Deleted photo: {}", target);
    } catch (IOException e) {
      log.warn("Failed to delete photo: {}", filename, e);
    }
  }

  private String resolveExtension(String originalFilename) {
    if (originalFilename == null || !originalFilename.contains(".")) return ".jpg";
    return originalFilename.substring(originalFilename.lastIndexOf('.'));
  }
}
