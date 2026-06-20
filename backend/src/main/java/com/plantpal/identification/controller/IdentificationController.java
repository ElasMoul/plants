package com.plantpal.identification.controller;

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
import com.plantpal.identification.service.IdentificationService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/identifications")
@Tag(name = "Identification", description = "AI-powered plant species identification via PlantNet")
@SecurityRequirement(name = "bearerAuth")
public class IdentificationController {

  private static final Logger log = LoggerFactory.getLogger(IdentificationController.class);

  private final IdentificationService identificationService;

  public IdentificationController(IdentificationService identificationService) {
    this.identificationService = identificationService;
  }

  @Operation(summary = "Submit one or more photos for async plant identification")
  @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<IdentificationPendingResponse>> analyze(
      @RequestPart("images") List<MultipartFile> images,
      @RequestParam(value = "organs", required = false) List<String> organs,
      @RequestParam(required = false) Long plantId,
      @RequestParam(required = false) Long speciesId) {

    Long userId = getCurrentUserId();
    log.info(
        "Identification requested: userId={}, images={}, plantId={}, speciesId={}",
        userId,
        images.size(),
        plantId,
        speciesId);

    try {
      IdentificationPendingResponse response =
          identificationService.submitIdentification(images, plantId, speciesId, userId, organs).get();
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(ApiResponse.success(response, "Analysis started — poll for result"));

    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof PlantPalException ppe) throw ppe;
      throw new PlantPalException("Identification failed", 500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PlantPalException("Identification was interrupted", 500);
    }
  }

  @Operation(summary = "Get all identifications for the current user")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<IdentificationResponse>>> getUserIdentifications(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Long userId = getCurrentUserId();
    Page<IdentificationResponse> page =
        identificationService.getUserIdentifications(userId, pageable);
    return ResponseEntity.ok(ApiResponse.success(page));
  }

  @Operation(summary = "Get a single identification by id")
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<IdentificationResponse>> getIdentification(
      @PathVariable Long id) {
    Long userId = getCurrentUserId();
    IdentificationResponse response = identificationService.getIdentification(id, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Get all identifications for a specific plant")
  @GetMapping("/plant/{plantId}")
  public ResponseEntity<ApiResponse<Page<IdentificationResponse>>> getPlantIdentifications(
      @PathVariable Long plantId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Long userId = getCurrentUserId();
    Page<IdentificationResponse> page =
        identificationService.getPlantIdentifications(plantId, userId, pageable);
    return ResponseEntity.ok(ApiResponse.success(page));
  }

  @Operation(summary = "Get cure advice for an identified disease region")
  @PostMapping("/{id}/cure-advice")
  public ResponseEntity<ApiResponse<CureAdviceResponse>> getCureAdvice(
      @PathVariable Long id, @RequestBody @Valid CureAdviceRequest req) {
    Long userId = getCurrentUserId();
    log.info("Cure advice requested: userId={}, identificationId={}", userId, id);
    try {
      CureAdviceResponse response = identificationService.getCureAdvice(id, req, userId).get();
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(ApiResponse.success(response, "Cure advice generated successfully"));
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof PlantPalException ppe) throw ppe;
      if (cause instanceof ResourceNotFoundException rne) throw rne;
      throw new PlantPalException("Cure advice generation failed", 500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PlantPalException("Cure advice request was interrupted", 500);
    }
  }

  @Operation(summary = "Add a generated care card (e.g. from cure advice) to the care plan")
  @PostMapping("/{id}/care-plan/cards")
  public ResponseEntity<ApiResponse<CarePlanDto>> addCareCard(
      @PathVariable Long id, @RequestBody @Valid AddCareCardRequest req) {
    Long userId = getCurrentUserId();
    log.info("Add care card requested: userId={}, identificationId={}", userId, id);
    CarePlanDto response = identificationService.addCareCard(id, req, userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Added to care plan"));
  }

  @Operation(summary = "Check whether the identified species already exists in the catalog")
  @GetMapping("/{id}/species-match")
  public ResponseEntity<ApiResponse<SpeciesMatchDto>> getSpeciesMatch(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    SpeciesMatchDto response = identificationService.getSpeciesMatch(id, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Confirm or reject the matched/suggested species for an identification")
  @PostMapping("/{id}/resolve-species")
  public ResponseEntity<ApiResponse<SpeciesMatchDto>> resolveSpecies(
      @PathVariable Long id, @RequestBody @Valid ResolveSpeciesRequest req) {
    Long userId = getCurrentUserId();
    log.info("Resolve species requested: userId={}, identificationId={}", userId, id);
    SpeciesMatchDto response = identificationService.resolveSpecies(id, req, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "List the user's existing plants of the identification's matched species")
  @GetMapping("/{id}/plant-match")
  public ResponseEntity<ApiResponse<PlantMatchDto>> getPlantMatch(@PathVariable Long id) {
    Long userId = getCurrentUserId();
    PlantMatchDto response = identificationService.getPlantMatch(id, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Attach an identification to an existing plant, or create a new one")
  @PostMapping("/{id}/resolve-plant")
  public ResponseEntity<ApiResponse<IdentificationResponse>> resolvePlant(
      @PathVariable Long id, @RequestBody @Valid ResolvePlantRequest req) {
    Long userId = getCurrentUserId();
    log.info("Resolve plant requested: userId={}, identificationId={}", userId, id);
    IdentificationResponse response = identificationService.resolvePlant(id, req, userId);
    return ResponseEntity.ok(ApiResponse.success(response, "Plant linked"));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
