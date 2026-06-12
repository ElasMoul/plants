package com.plantpal.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

  String savePhoto(MultipartFile file);

  void deletePhoto(String url);
}
