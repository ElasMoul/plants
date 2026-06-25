package com.plantpal.identification.service;

import com.plantpal.identification.dto.AddCareCardRequest;
import com.plantpal.identification.dto.CarePlanDto;
import com.plantpal.identification.dto.CureAdviceRequest;
import com.plantpal.identification.dto.CureAdviceResponse;
import com.plantpal.identification.dto.IdentificationPendingResponse;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.dto.PlantMatchDto;
import com.plantpal.identification.dto.ResolvePlantRequest;
import com.plantpal.identification.dto.ResolveSpeciesRequest;
import com.plantpal.identification.dto.SpeciesMatchDto;
import com.plantpal.identification.event.IdentificationRequestedEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface IdentificationService {

  CompletableFuture<IdentificationPendingResponse> submitIdentification(
      List<MultipartFile> images, Long plantId, Long speciesId, Long userId, List<String> organs);

  void processIdentification(IdentificationRequestedEvent event);

  IdentificationResponse getIdentification(Long id, Long userId);

  Page<IdentificationResponse> getUserIdentifications(Long userId, Pageable pageable);

  Page<IdentificationResponse> getPlantIdentifications(
      Long plantId, Long userId, Pageable pageable);

  CompletableFuture<CureAdviceResponse> getCureAdvice(Long id, CureAdviceRequest req, Long userId);

  CarePlanDto addCareCard(Long id, AddCareCardRequest req, Long userId);

  SpeciesMatchDto getSpeciesMatch(Long id, Long userId);

  SpeciesMatchDto resolveSpecies(Long id, ResolveSpeciesRequest req, Long userId);

  PlantMatchDto getPlantMatch(Long id, Long userId);

  IdentificationResponse resolvePlant(Long id, ResolvePlantRequest req, Long userId);

  IdentificationResponse retryIdentification(Long id, Long userId);
}
