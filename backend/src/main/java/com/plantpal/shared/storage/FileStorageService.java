package com.plantpal.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

  String savePhoto(MultipartFile file);

  byte[] loadPhotoBytes(String photoUrl);

  /**
   * Replaces the bytes behind an existing {@code /photos/<filename>} URL, keeping the URL valid.
   */
  void overwritePhoto(String photoUrl, byte[] bytes);

  void deletePhoto(String url);
}
