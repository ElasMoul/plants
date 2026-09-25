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
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
      named = "PLANTPAL_LIVE_TRANSLATION_TEST",
      matches = "true")
  void hostedArabicSmokePreservesTemperatureRangesAndProducesArabic() throws Exception {
    var hosted =
        new DeepSeekDirectClient(
            System.getenv()
                .getOrDefault("DEEPSEEK_HOSTED_BASE_URL", "https://api.deepseek.com/anthropic"),
            System.getenv("DEEPSEEK_HOSTED_API_KEY"),
            System.getenv().getOrDefault("DEEPSEEK_DIRECT_MODEL", "deepseek-flash"));
    var live =
        new TranslationClient(
            hosted, mock(AnthropicClient.class), mock(OllamaClient.class), mapper, usage);
    var texts =
        List.of(
            "Keep it Warm",
            "Ideal temperatures are between 55°F and 80°F (13°C - 27°C).",
            "Water deeply, then wait until the soil is completely dry before watering again.",
            "Keep temperatures between 15°C and 30°C — avoid cold drafts and frost.");
    var result = live.translate(texts, 4L, "ar");
    assertThat(result).hasSize(texts.size());
    assertThat(result.values()).allMatch(value -> value.matches("(?s).*[\\u0600-\\u06ff].*"));
  }

  @Test
  void unfenceStripsJsonFencesLikeTheFormerRegex() {
    assertThat(TranslationClient.unfence("```json\n[\"a\"]\n```")).isEqualTo("[\"a\"]");
    assertThat(TranslationClient.unfence("  ```[\"a\"]```  ")).isEqualTo("[\"a\"]");
    assertThat(TranslationClient.unfence("```json [\"a\"]")).isEqualTo("[\"a\"]");
    assertThat(TranslationClient.unfence("[\"a\"]")).isEqualTo("[\"a\"]");
    assertThat(TranslationClient.unfence("```")).isEmpty();
  }

  @Test
  void unfenceStaysLinearOnLongWhitespace() {
    String reply = "[\"a\"]" + " ".repeat(40_000) + "x";
    String result =
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            java.time.Duration.ofSeconds(1), () -> TranslationClient.unfence(reply));
    assertThat(result).startsWith("[\"a\"]").endsWith("x");
  }

  @Test
  void repairsLocalizedUnitsWithoutChangingTheOriginalAmounts() throws Exception {
    when(provider.isAvailable()).thenReturn(true);
    when(provider.chat(anyString(), anyString()))
        .thenReturn("[\"استخدم 5 مل كل 7 أيام.\"]")
        .thenReturn("[\"استخدم __PP_VALUE_0__ كل __PP_VALUE_1__ أيام.\"]");
    assertThat(client.translate(List.of("Use 5 ml every 7 days."), 4L, "ar"))
        .containsEntry("Use 5 ml every 7 days.", "استخدم 5 ml كل 7 أيام.");
    verify(usage, times(2)).consumeAi(4L, false);
  }

  @Test
  void rejectsMissingProtectedNumbers() {
    var text = new ProtectedTranslationText("Keep between 15°C and 30°C.");
    assertThat(text.masked()).isEqualTo("Keep between __PP_VALUE_0__ and __PP_VALUE_1__.");
    assertThatThrownBy(() -> text.restore("__PP_VALUE_0__")).isInstanceOf(RuntimeException.class);
  }

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
