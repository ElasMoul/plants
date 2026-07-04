package com.plantpal.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

  String savePhoto(MultipartFile file);

  byte[] loadPhotoBytes(String photoUrl);

  void deletePhoto(String url);
}
