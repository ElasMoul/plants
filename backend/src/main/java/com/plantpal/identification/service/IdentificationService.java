package com.plantpal.identification.service;

import com.plantpal.identification.dto.CureAdviceRequest;
import com.plantpal.identification.dto.CureAdviceResponse;
import com.plantpal.identification.dto.IdentificationPendingResponse;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface IdentificationService {

  CompletableFuture<IdentificationPendingResponse> submitIdentification(
      List<MultipartFile> images, Long plantId, Long userId, List<String> organs);

  void processIdentification(IdentificationRequestedEvent event);

  IdentificationResponse getIdentification(Long id, Long userId);

  Page<IdentificationResponse> getPlantIdentifications(
      Long plantId, Long userId, Pageable pageable);

  CompletableFuture<CureAdviceResponse> getCureAdvice(Long id, CureAdviceRequest req, Long userId);
}
