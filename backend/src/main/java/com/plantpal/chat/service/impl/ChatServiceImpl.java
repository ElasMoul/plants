package com.plantpal.chat.service.impl;

import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;
import com.plantpal.chat.service.ChatService;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.treatment.entity.Treatment;
import com.plantpal.treatment.repository.TreatmentRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

  private static final int CHAT_RATE_LIMIT = 30;
  private static final int GARDEN_CONTEXT_PAGE_SIZE = 50;

  private static final String SYSTEM_PROMPT_TEMPLATE =
      """
      You are PlantPal, a friendly and knowledgeable plant care assistant.

      The user's garden:
      %s

      Guidelines:
      - Be warm, practical, and specific to the user's actual plants when relevant.
      - Keep answers concise: 2-4 short paragraphs.
      - If unsure, say so — never guess about plant health.
      - If the question is about a plant not in their garden, answer generally.
      """;

  private final OllamaClient ollamaClient;
  private final PlantRepository plantRepository;
  private final IdentificationRepository identificationRepository;
  private final TreatmentRepository treatmentRepository;

  private final Map<Long, Bucket> chatBuckets = new ConcurrentHashMap<>();

  public ChatServiceImpl(
      OllamaClient ollamaClient,
      PlantRepository plantRepository,
      IdentificationRepository identificationRepository,
      TreatmentRepository treatmentRepository) {
    this.ollamaClient = ollamaClient;
    this.plantRepository = plantRepository;
    this.identificationRepository = identificationRepository;
    this.treatmentRepository = treatmentRepository;
  }

  @Override
  public ChatResponse chat(ChatRequest request, Long userId) {
    if (!consumeRateLimit(userId)) {
      throw new PlantPalException("Chat rate limit reached — try again later", 429);
    }

    String contextBlock =
        request.getPlantId() != null
            ? buildPlantContext(request.getPlantId(), userId) + "\n\n" + buildGardenContext(userId)
            : buildGardenContext(userId);
    String prompt =
        SYSTEM_PROMPT_TEMPLATE.formatted(contextBlock) + "\n\nUser: " + request.getMessage();

    log.info("Chat request: userId={}", userId);
    String reply = ollamaClient.chat(prompt);
    return ChatResponse.builder().reply(reply).build();
  }

  private String buildGardenContext(Long userId) {
    Page<Plant> plants =
        plantRepository.findAllByUserIdAndStatus(
            userId, PlantStatus.ACTIVE, PageRequest.of(0, GARDEN_CONTEXT_PAGE_SIZE));
    if (plants.isEmpty()) {
      return "No plants in the garden yet.";
    }
    return plants.getContent().stream().map(this::formatPlant).collect(Collectors.joining("\n"));
  }

  private String buildPlantContext(Long plantId, Long userId) {
    Plant plant =
        plantRepository
            .findByIdAndUserId(plantId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Plant", plantId));

    String label =
        plant.getSpecies() != null
            ? plant.getSpecies()
            : (plant.getCommonName() != null ? plant.getCommonName() : "unknown species");

    StringBuilder context =
        new StringBuilder(
            "The user is asking specifically about their plant '"
                + plant.getNickname()
                + "' ("
                + label
                + ").");

    List<Identification> latest = identificationRepository.findLatestPerPlant(List.of(plantId));
    if (!latest.isEmpty()) {
      Identification identification = latest.get(0);
      context
          .append(" Its last scan (")
          .append(identification.getCreatedAt())
          .append(") showed health status: ")
          .append(identification.getHealthStatus())
          .append(".");
    }

    if (plant.getActiveTreatmentId() != null) {
      treatmentRepository
          .findById(plant.getActiveTreatmentId())
          .map(Treatment::getDiseaseName)
          .ifPresent(
              diseaseName ->
                  context
                      .append(" It currently has an active treatment in progress for ")
                      .append(diseaseName)
                      .append("."));
    }

    return context.toString();
  }

  private String formatPlant(Plant plant) {
    String label =
        plant.getCommonName() != null
            ? plant.getCommonName()
            : (plant.getSpecies() != null ? plant.getSpecies() : "unknown species");
    return "- " + plant.getNickname() + " (" + label + ")";
  }

  private boolean consumeRateLimit(Long userId) {
    Bucket bucket =
        chatBuckets.computeIfAbsent(
            userId,
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(CHAT_RATE_LIMIT)
                            .refillIntervally(CHAT_RATE_LIMIT, Duration.ofHours(1))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }
}
