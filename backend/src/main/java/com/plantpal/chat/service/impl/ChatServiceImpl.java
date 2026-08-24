package com.plantpal.chat.service.impl;

import com.plantpal.chat.dto.ChatMessageDto;
import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;
import com.plantpal.chat.service.ChatService;
import com.plantpal.chat.service.GardenContextService;
import com.plantpal.gateway.GatewayClient;
import com.plantpal.gateway.GatewayProperties;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.identification.entity.Identification;
import com.plantpal.identification.repository.IdentificationRepository;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.ai.PromptSanitizer;
import com.plantpal.shared.exception.PlantPalException;
import com.plantpal.shared.exception.ResourceNotFoundException;
import com.plantpal.treatment.entity.Treatment;
import com.plantpal.treatment.repository.TreatmentRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.platform.contracts.aigateway.AiRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

  // Bounds an ever-growing client-side history array from blowing up the prompt -- never trust
  // unbounded client input, same convention as ActionPlanValidator's step-list truncation.
  private static final int MAX_HISTORY_MESSAGES = 10;

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
  private final AnthropicClient anthropicClient;
  private final PlantRepository plantRepository;
  private final IdentificationRepository identificationRepository;
  private final TreatmentRepository treatmentRepository;
  private final GardenContextService gardenContextService;
  private final GatewayClient gatewayClient;
  private final GatewayProperties gatewayProperties;
  private final int chatMessagesPerHour;

  private final Map<Long, Bucket> chatBuckets = new ConcurrentHashMap<>();

  public ChatServiceImpl(
      OllamaClient ollamaClient,
      AnthropicClient anthropicClient,
      PlantRepository plantRepository,
      IdentificationRepository identificationRepository,
      TreatmentRepository treatmentRepository,
      GardenContextService gardenContextService,
      GatewayClient gatewayClient,
      GatewayProperties gatewayProperties,
      @Value("${app.rate-limit.chat-messages-per-hour:10}") int chatMessagesPerHour) {
    this.ollamaClient = ollamaClient;
    this.anthropicClient = anthropicClient;
    this.plantRepository = plantRepository;
    this.identificationRepository = identificationRepository;
    this.treatmentRepository = treatmentRepository;
    this.gardenContextService = gardenContextService;
    this.gatewayClient = gatewayClient;
    this.gatewayProperties = gatewayProperties;
    this.chatMessagesPerHour = chatMessagesPerHour;
  }

  @Override
  public ChatResponse chat(ChatRequest request, Long userId) {
    if (!consumeRateLimit(userId)) {
      throw new PlantPalException("Chat rate limit reached — try again later", 429);
    }

    log.info("Chat request: userId={}", userId);
    if (gatewayProperties.enabled()) {
      return ChatResponse.builder().reply(gatewayChat(request, userId)).build();
    }
    if (anthropicClient.isAvailable()) {
      return ChatResponse.builder().reply(anthropicChat(request, userId)).build();
    }
    String prompt = buildPrompt(request, userId);
    String reply = ollamaClient.chat(prompt);
    return ChatResponse.builder().reply(reply).build();
  }

  @Override
  public void chatStream(ChatRequest request, Long userId, Consumer<String> onToken) {
    if (!consumeRateLimit(userId)) {
      throw new PlantPalException("Chat rate limit reached — try again later", 429);
    }

    log.info("Chat stream request: userId={}", userId);
    if (gatewayProperties.enabled()) {
      // ai-gateway is buffered-only (no SSE passthrough yet, a real chunk for later) — call once
      // and invoke onToken a single time with the full result, unlike the real per-token Ollama
      // stream below.
      onToken.accept(gatewayChat(request, userId));
      return;
    }
    if (anthropicClient.isAvailable()) {
      // Buffered like the gateway path — AnthropicClient has no SSE support yet.
      onToken.accept(anthropicChat(request, userId));
      return;
    }
    String prompt = buildPrompt(request, userId);
    ollamaClient.chatStream(prompt, onToken);
  }

  /**
   * Direct Claude path — took over from Ollama-only chat after GitHub Models' upstream retirement
   * made Claude the primary hosted provider; Ollama remains the un-keyed local fallback.
   */
  private String anthropicChat(ChatRequest request, Long userId) {
    return anthropicClient.chat(
        buildSystemPromptBlock(request, userId), PromptSanitizer.delimit(request.getMessage()));
  }

  /** Gateway routing (D022, Chunk 3) — same system prompt + user message as the direct path. */
  private String gatewayChat(ChatRequest request, Long userId) {
    String systemPrompt = buildSystemPromptBlock(request, userId);
    String userMessage = PromptSanitizer.delimit(request.getMessage());
    AiRequest aiRequest =
        new AiRequest()
            .prompt(userMessage)
            .modelHint(ollamaClient.getModel())
            .appId("plantpal")
            .userId(String.valueOf(userId))
            .putContextItem("systemPrompt", systemPrompt);
    return gatewayClient.request(aiRequest).getResult();
  }

  /**
   * Everything the direct Ollama path prepends before the user's own message: system prompt,
   * garden/plant context, and prior conversation turns.
   */
  private String buildSystemPromptBlock(ChatRequest request, Long userId) {
    String contextBlock =
        request.getPlantId() != null
            ? buildPlantContext(request.getPlantId(), userId)
                + "\n\n"
                + gardenContextService.buildGardenContext(userId)
            : gardenContextService.buildGardenContext(userId);
    String historyBlock = buildHistoryBlock(request.getHistory());
    return SYSTEM_PROMPT_TEMPLATE.formatted(contextBlock) + historyBlock;
  }

  /**
   * Shared by the synchronous {@link #chat} and the streaming path -- same context, same prompt.
   */
  private String buildPrompt(ChatRequest request, Long userId) {
    return buildSystemPromptBlock(request, userId)
        + "\n\nUser: "
        + PromptSanitizer.delimit(request.getMessage());
  }

  private String buildHistoryBlock(List<ChatMessageDto> history) {
    if (history == null || history.isEmpty()) {
      return "";
    }
    List<ChatMessageDto> recent =
        history.size() > MAX_HISTORY_MESSAGES
            ? history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size())
            : history;
    String turns =
        recent.stream()
            .map(m -> capitalize(m.getRole()) + ": " + m.getContent())
            .collect(Collectors.joining("\n"));
    return "\n\nPrevious conversation:\n" + turns;
  }

  private String capitalize(String role) {
    if (role == null || role.isBlank()) {
      return role;
    }
    return Character.toUpperCase(role.charAt(0)) + role.substring(1);
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

  private boolean consumeRateLimit(Long userId) {
    Bucket bucket =
        chatBuckets.computeIfAbsent(
            userId,
            id ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.builder()
                            .capacity(chatMessagesPerHour)
                            .refillIntervally(chatMessagesPerHour, Duration.ofHours(1))
                            .build())
                    .build());
    return bucket.tryConsume(1);
  }
}
