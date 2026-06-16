package com.plantpal.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

  String savePhoto(MultipartFile file);

  byte[] loadPhoto(String url);

  void deletePhoto(String url);
}
