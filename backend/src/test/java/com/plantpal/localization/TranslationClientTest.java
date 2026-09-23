package com.plantpal.localization;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.identification.client.AnthropicClient;
import com.plantpal.identification.client.DeepSeekDirectClient;
import com.plantpal.identification.client.OllamaClient;
import com.plantpal.user.service.UsageService;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationClientTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final DeepSeekDirectClient provider = mock(DeepSeekDirectClient.class);
  private final UsageService usage = mock(UsageService.class);
  private final TranslationClient client =
      new TranslationClient(
          provider, mock(AnthropicClient.class), mock(OllamaClient.class), mapper, usage);

  @Test
  void requestsArabicAndPreservesLatinUnitsAndNumbers() throws Exception {
    when(provider.isAvailable()).thenReturn(true);
    when(provider.chat(anyString(), anyString())).thenReturn("[\"استخدم 5 ml كل 7 أيام.\"]");
    assertThat(client.translate(List.of("Use 5 ml every 7 days."), 4L, "ar"))
        .containsEntry("Use 5 ml every 7 days.", "استخدم 5 ml كل 7 أيام.");
    verify(provider).chat(contains("Modern Standard Arabic"), anyString());
  }

  @Test
  void acceptsFrenchProseWithoutChangingDosages() throws Exception {
    var source = List.of("Mix 5 ml in 1 L. Repeat after 7 days.");
    var result =
        client.validate(
            source, mapper.readTree("[\"Mélangez 5 ml dans 1 L. Répétez après 7 jours.\"]"));
    assertThat(result.get(source.get(0))).contains("5 ml", "1 L", "7 jours");
  }

  @Test
  void rejectsChangedAmountsUnitsMissingAndExtraText() throws Exception {
    var source = List.of("Apply 5 ml every 7 days.");
    for (String invalid :
        List.of(
            "[\"Appliquez 50 ml tous les 7 jours.\"]",
            "[\"Appliquez 5 g tous les 7 jours.\"]",
            "[]",
            "[\"\"]",
            "[1]",
            "[\"Appliquez 7 ml tous les 5 jours.\"]")) {
      var node = mapper.readTree(invalid);
      assertThatThrownBy(() -> client.validate(source, node)).isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void usesNativeDeepSeekAndCountsOnlyTheTranslationCall() throws Exception {
    when(provider.isAvailable()).thenReturn(true);
    when(provider.chat(anyString(), anyString())).thenReturn("[\"Arrosez tous les 7 jours.\"]");
    assertThat(client.translate(List.of("Water every 7 days."), 4L, "fr"))
        .containsEntry("Water every 7 days.", "Arrosez tous les 7 jours.");
    verify(usage).consumeAi(4L, false);
    verify(provider).chat(contains("French"), contains("Water every 7 days."));
  }

  @Test
  void quotaFailureNeverCallsTheProvider() {
    doThrow(new IllegalStateException("quota")).when(usage).consumeAi(4L, false);
    assertThatThrownBy(() -> client.translate(List.of("Water."), 4L, "fr"))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(provider);
  }
}
