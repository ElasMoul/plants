package com.plantpal.identification.service;

import com.plantpal.identification.dto.CureAdviceRequest;
import com.plantpal.identification.dto.CureAdviceResponse;
import com.plantpal.identification.dto.IdentificationResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface IdentificationService {

  CompletableFuture<IdentificationResponse> identify(
      List<MultipartFile> images, List<String> organs, Long plantId, Long userId);

  Page<IdentificationResponse> getPlantIdentifications(
      Long plantId, Long userId, Pageable pageable);

  CompletableFuture<CureAdviceResponse> getCureAdvice(Long id, CureAdviceRequest req, Long userId);
}
