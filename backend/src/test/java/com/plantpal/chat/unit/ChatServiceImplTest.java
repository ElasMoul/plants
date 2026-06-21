package com.plantpal.chat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.chat.dto.ChatMessageDto;
import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;
import com.plantpal.chat.service.impl.ChatServiceImpl;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl — Unit Tests")
class ChatServiceImplTest {

  @Mock private OllamaClient ollamaClient;
  @Mock private PlantRepository plantRepository;
  @Mock private IdentificationRepository identificationRepository;
  @Mock private TreatmentRepository treatmentRepository;

  private ChatServiceImpl chatService;

  private static final Long USER_ID = 1L;

  @BeforeEach
  void setUp() {
    chatService =
        new ChatServiceImpl(
            ollamaClient, plantRepository, identificationRepository, treatmentRepository);
  }

  private ChatRequest request(String message) {
    return ChatRequest.builder().message(message).build();
  }

  private ChatRequest request(String message, Long plantId) {
    return ChatRequest.builder().message(message).plantId(plantId).build();
  }

  private ChatRequest requestWithHistory(String message, List<ChatMessageDto> history) {
    return ChatRequest.builder().message(message).history(history).build();
  }

  private ChatMessageDto turn(String role, String content) {
    return ChatMessageDto.builder().role(role).content(content).build();
  }

  @Nested
  @DisplayName("chat()")
  class Chat {

    @Test
    @DisplayName("should build garden context from active plants and return Ollama's reply")
    void shouldReturnReplyWithGardenContext() {
      Plant monstera =
          Plant.builder().id(1L).userId(USER_ID).nickname("Monty").commonName("Monstera").build();
      Plant unidentified = Plant.builder().id(2L).userId(USER_ID).nickname("Mystery Plant").build();
      Page<Plant> page = new PageImpl<>(List.of(monstera, unidentified));
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(page);
      when(ollamaClient.chat(any())).thenReturn("Your Monstera looks happy!");

      ChatResponse response = chatService.chat(request("How is my Monstera doing?"), USER_ID);

      assertThat(response.getReply()).isEqualTo("Your Monstera looks happy!");

      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chat(promptCaptor.capture());
      String prompt = promptCaptor.getValue();

      assertThat(prompt).contains("How is my Monstera doing?");
      assertThat(prompt).contains("- Monty (Monstera)");
      assertThat(prompt).contains("- Mystery Plant (unknown species)");
      assertThat(prompt).contains("You are PlantPal");
    }

    @Test
    @DisplayName("should use placeholder garden context when user has no active plants")
    void shouldHandleEmptyGarden() {
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of()));
      when(ollamaClient.chat(any())).thenReturn("General plant care advice.");

      ChatResponse response = chatService.chat(request("What plant should I get?"), USER_ID);

      assertThat(response.getReply()).isEqualTo("General plant care advice.");

      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chat(promptCaptor.capture());
      assertThat(promptCaptor.getValue()).contains("No plants in the garden yet.");
    }

    @Test
    @DisplayName("should throw 429 when chat rate limit is exceeded")
    void shouldThrowWhenRateLimited() {
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of()));
      when(ollamaClient.chat(any())).thenReturn("reply");

      for (int i = 0; i < 30; i++) {
        chatService.chat(request("msg " + i), USER_ID);
      }

      assertThatThrownBy(() -> chatService.chat(request("one too many"), USER_ID))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("rate limit");
    }
  }

  @Nested
  @DisplayName("chat() with plantId")
  class ChatWithPlantId {

    private static final Long PLANT_ID = 5L;

    @Test
    @DisplayName("should prepend plant-specific context when plantId is owned by the user")
    void shouldIncludePlantSpecificContext() {
      Plant monstera =
          Plant.builder()
              .id(PLANT_ID)
              .userId(USER_ID)
              .nickname("Monty")
              .species("Monstera deliciosa")
              .activeTreatmentId(9L)
              .build();
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.of(monstera));
      Identification scan = Identification.builder().healthStatus("ISSUES_DETECTED").build();
      scan.setCreatedAt(Instant.parse("2026-06-15T10:00:00Z"));
      when(identificationRepository.findLatestPerPlant(List.of(PLANT_ID)))
          .thenReturn(List.of(scan));
      Treatment treatment = Treatment.builder().id(9L).diseaseName("Root Rot").build();
      when(treatmentRepository.findById(9L)).thenReturn(Optional.of(treatment));
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of()));
      when(ollamaClient.chat(any())).thenReturn("Here's what's going on with Monty.");

      ChatResponse response = chatService.chat(request("How is Monty doing?", PLANT_ID), USER_ID);

      assertThat(response.getReply()).isEqualTo("Here's what's going on with Monty.");
      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chat(promptCaptor.capture());
      String prompt = promptCaptor.getValue();
      assertThat(prompt)
          .contains("The user is asking specifically about their plant 'Monty'")
          .contains("Monstera deliciosa")
          .contains("showed health status: ISSUES_DETECTED")
          .contains("active treatment in progress for Root Rot");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when the plant is not owned by the user")
    void shouldThrowWhenPlantNotOwned() {
      when(plantRepository.findByIdAndUserId(PLANT_ID, USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> chatService.chat(request("How is Monty doing?", PLANT_ID), USER_ID))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("chat() with history")
  class ChatWithHistory {

    @BeforeEach
    void stubEmptyGarden() {
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of()));
      when(ollamaClient.chat(any())).thenReturn("reply");
    }

    @Test
    @DisplayName("should include prior turns in the prompt, oldest first")
    void shouldIncludeHistoryInPrompt() {
      List<ChatMessageDto> history =
          List.of(turn("user", "What plant is this?"), turn("assistant", "A Monstera deliciosa."));

      chatService.chat(requestWithHistory("How often should I water it?", history), USER_ID);

      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chat(promptCaptor.capture());
      String prompt = promptCaptor.getValue();
      assertThat(prompt)
          .contains("Previous conversation:")
          .contains("User: What plant is this?")
          .contains("Assistant: A Monstera deliciosa.")
          .contains("User: How often should I water it?");
      assertThat(prompt.indexOf("What plant is this?"))
          .isLessThan(prompt.indexOf("How often should I water it?"));
    }

    @Test
    @DisplayName("should truncate to the most recent 10 messages")
    void shouldTruncateLongHistory() {
      List<ChatMessageDto> history = new java.util.ArrayList<>();
      for (int i = 0; i < 15; i++) {
        history.add(turn("user", "message " + i));
      }

      chatService.chat(requestWithHistory("latest question", history), USER_ID);

      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chat(promptCaptor.capture());
      String prompt = promptCaptor.getValue();
      assertThat(prompt).doesNotContain("message 0").doesNotContain("message 4");
      assertThat(prompt).contains("message 5").contains("message 14");
    }

    @Test
    @DisplayName("should behave identically to before when history is absent")
    void shouldBeUnchangedWithoutHistory() {
      chatService.chat(request("plain question"), USER_ID);

      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chat(promptCaptor.capture());
      assertThat(promptCaptor.getValue()).doesNotContain("Previous conversation:");
    }
  }

  @Nested
  @DisplayName("chatStream()")
  class ChatStream {

    @Test
    @DisplayName("should delegate to ollamaClient.chatStream() with the same prompt as chat()")
    void shouldDelegateToOllamaClientStream() {
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of()));

      List<String> tokens = new java.util.ArrayList<>();
      chatService.chatStream(request("How often should I water?"), USER_ID, tokens::add);

      ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
      verify(ollamaClient).chatStream(promptCaptor.capture(), any());
      assertThat(promptCaptor.getValue()).contains("How often should I water?");
    }

    @Test
    @DisplayName("should check the rate limit before any streaming starts")
    void shouldCheckRateLimitBeforeStreaming() {
      when(plantRepository.findAllByUserIdAndStatus(
              eq(USER_ID), eq(PlantStatus.ACTIVE), any(PageRequest.class)))
          .thenReturn(new PageImpl<>(List.of()));

      for (int i = 0; i < 30; i++) {
        chatService.chatStream(request("msg " + i), USER_ID, token -> {});
      }

      assertThatThrownBy(
              () -> chatService.chatStream(request("one too many"), USER_ID, token -> {}))
          .isInstanceOf(PlantPalException.class)
          .hasMessageContaining("rate limit");

      // 30 legitimate calls went through; the 31st was rejected before reaching ollamaClient.
      verify(ollamaClient, org.mockito.Mockito.times(30)).chatStream(any(), any());
    }
  }
}
