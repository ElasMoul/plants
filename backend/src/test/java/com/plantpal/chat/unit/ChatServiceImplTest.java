package com.plantpal.chat.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;
import com.plantpal.chat.service.impl.ChatServiceImpl;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.plant.entity.Plant;
import com.plantpal.plant.entity.PlantStatus;
import com.plantpal.plant.repository.PlantRepository;
import com.plantpal.shared.exception.PlantPalException;
import java.util.List;
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

  private ChatServiceImpl chatService;

  private static final Long USER_ID = 1L;

  @BeforeEach
  void setUp() {
    chatService = new ChatServiceImpl(ollamaClient, plantRepository);
  }

  private ChatRequest request(String message) {
    return ChatRequest.builder().message(message).build();
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
}
