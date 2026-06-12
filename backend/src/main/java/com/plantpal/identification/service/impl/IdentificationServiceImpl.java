package com.plantpal.identification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.PlantNetClient;
import com.plantpal.identification.dto.IdentificationResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResponse;
import com.plantpal.identification.dto.plantnet.PlantNetResult;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.entity.IdentificationStatus;
import com.plantpal.identification.mapper.IdentificationMapper;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.identification.service.IdentificationService;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.shared.storage.FileStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdentificationServiceImpl implements IdentificationService {

  private static final Logger log = LoggerFactory.getLogger(IdentificationServiceImpl.class);

  private static final int MAX_IMAGES = 5;
  private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
  private static final List<String> ALLOWED_TYPES =
      List.of("image/jpeg", "image/png", "image/webp");

  private final PlantNetClient plantNetClient;
  private final IdentificationRepository identificationRepository;
  private final IdentificationMapper identificationMapper;
  private final PlantRepository plantRepository;
  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper;

  public IdentificationServiceImpl(
      PlantNetClient plantNetClient,
      IdentificationRepository identificationRepository,
      IdentificationMapper identificationMapper,
      PlantRepository plantRepository,
      FileStorageService fileStorageService,
      ObjectMapper objectMapper) {
    this.plantNetClient = plantNetClient;
    this.identificationRepository = identificationRepository;
    this.identificationMapper = identificationMapper;
    this.plantRepository = plantRepository;
    this.fileStorageService = fileStorageService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Async("aiTaskExecutor")
  public CompletableFuture<IdentificationResponse> identify(
      List<MultipartFile> images, List<String> organs, Long plantId, Long userId) {

    validateImages(images);

    Identification identification = null;
    try {
      // Step 2: Save photos, collect URLs
      List<String> photoUrls = new ArrayList<>();
      for (MultipartFile image : images) {
        photoUrls.add(fileStorageService.savePhoto(image));
      }

      // Step 3: Persist with PENDING status
      identification =
          Identification.builder()
              .userId(userId)
              .plantId(plantId)
              .photoUrl(photoUrls.get(0))
              .status(IdentificationStatus.PENDING)
              .build();
      identification = identificationRepository.save(identification);
      log.info("Identification created: id={}, userId={}", identification.getId(), userId);

      // Step 4: Call PlantNet
      PlantNetResponse plantNetResponse = plantNetClient.identify(images, organs);

      // Step 5: Map top result → update entity
      PlantNetResult topResult = plantNetResponse.results().get(0);
      String scientificName = topResult.species().scientificNameWithoutAuthor();
      List<String> commonNames = topResult.species().commonNames();
      String commonName =
          (commonNames != null && !commonNames.isEmpty()) ? commonNames.get(0) : null;

      identification.setScientificName(scientificName);
      identification.setCommonName(commonName);
      identification.setConfidence(topResult.score());
      identification.setRawResponse(serializeRawResponse(plantNetResponse));
      identification.setStatus(IdentificationStatus.COMPLETED);
      identification = identificationRepository.save(identification);

      // Step 6: Update plant species if owned by user
      if (plantId != null && plantRepository.existsByIdAndUserId(plantId, userId)) {
        plantRepository
            .findByIdAndUserId(plantId, userId)
            .ifPresent(
                plant -> {
                  plant.setSpecies(scientificName);
                  plant.setCommonName(commonName);
                  plantRepository.save(plant);
                  log.info(
                      "Updated plant species: plantId={}, species={}", plantId, scientificName);
                });
      }

      // Step 8: Build and return response with top 3 results
      List<PlantNetResult> topResults = plantNetResponse.results().stream().limit(3).toList();
      IdentificationResponse response = buildResponse(identification, topResults);
      return CompletableFuture.completedFuture(response);

    } catch (PlantPalException e) {
      markFailed(identification);
      throw e;
    } catch (Exception e) {
      log.error("Identification failed for userId={}", userId, e);
      markFailed(identification);
      throw new PlantPalException("Identification failed: " + e.getMessage(), 500);
    }
  }

  @Override
  public Page<IdentificationResponse> getPlantIdentifications(
      Long plantId, Long userId, Pageable pageable) {
    if (!plantRepository.existsByIdAndUserId(plantId, userId)) {
      throw new ResourceNotFoundException("Plant not found");
    }
    return identificationRepository
        .findByPlantIdOrderByCreatedAtDesc(plantId, pageable)
        .map(identificationMapper::toResponse);
  }

  private void validateImages(List<MultipartFile> images) {
    if (images == null || images.isEmpty() || images.size() > MAX_IMAGES) {
      throw new ValidationException("Between 1 and " + MAX_IMAGES + " images are required");
    }
    for (MultipartFile image : images) {
      if (image.getSize() > MAX_IMAGE_BYTES) {
        throw new ValidationException("Each image must be 10 MB or smaller");
      }
      String contentType = image.getContentType();
      if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
        throw new ValidationException("Images must be JPEG, PNG, or WebP");
      }
    }
  }

  private void markFailed(Identification identification) {
    if (identification == null) return;
    try {
      identification.setStatus(IdentificationStatus.FAILED);
      identificationRepository.save(identification);
    } catch (Exception ex) {
      log.error("Failed to mark identification as FAILED: id={}", identification.getId(), ex);
    }
  }

  private String serializeRawResponse(PlantNetResponse response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize PlantNet raw response", e);
      return null;
    }
  }

  private IdentificationResponse buildResponse(
      Identification entity, List<PlantNetResult> topResults) {
    return IdentificationResponse.builder()
        .id(entity.getId())
        .plantId(entity.getPlantId())
        .scientificName(entity.getScientificName())
        .commonName(entity.getCommonName())
        .confidence(entity.getConfidence())
        .status(entity.getStatus())
        .photoUrl(entity.getPhotoUrl())
        .createdAt(entity.getCreatedAt())
        .topResults(topResults)
        .build();
  }
}
