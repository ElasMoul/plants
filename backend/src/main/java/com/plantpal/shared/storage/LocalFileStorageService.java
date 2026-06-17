package com.plantpal.shared.storage;

import com.plantpal.shared.exception.PlantPalException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("!prod")
public class LocalFileStorageService implements FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

  private final Path storageRoot;

  public LocalFileStorageService(
      @Value("${app.storage.local-path:/tmp/plantpal/photos}") String localPath) {
    this.storageRoot = Paths.get(localPath);
    try {
      Files.createDirectories(storageRoot);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create storage directory: " + localPath, e);
    }
  }

  @Override
  public String savePhoto(MultipartFile file) {
    String extension = resolveExtension(file.getOriginalFilename());
    String filename = UUID.randomUUID() + extension;
    Path target = storageRoot.resolve(filename);

    try {
      Files.write(target, file.getBytes());
      log.debug("Saved photo: {}", target);
      return "/photos/" + filename;
    } catch (IOException e) {
      log.error("Failed to save photo: {}", filename, e);
      throw new PlantPalException("Failed to save photo", 500);
    }
  }

  @Override
  public byte[] loadPhoto(String url) {
    if (url == null || !url.startsWith("/photos/")) {
      throw new PlantPalException("Invalid photo URL", 400);
    }
    String filename = url.substring("/photos/".length());
    Path target = storageRoot.resolve(filename);
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
