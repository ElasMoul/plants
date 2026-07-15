package com.plantpal.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HtmlSanitizer")
class HtmlSanitizerTest {

  @Test
  @DisplayName("strips a <script> tag entirely, including its content")
  void stripsScriptTag() {
    String result = HtmlSanitizer.sanitize("<script>alert('xss')</script>Hello");

    assertThat(result).doesNotContain("<script>").doesNotContain("alert");
    assertThat(result).contains("Hello");
  }

  @Test
  @DisplayName("strips an event-handler-bearing tag like <img onerror=...>")
  void stripsImgWithEventHandler() {
    String result = HtmlSanitizer.sanitize("<img src=x onerror=\"alert(1)\">Living room");

    assertThat(result).doesNotContain("onerror").doesNotContain("<img");
    assertThat(result).contains("Living room");
  }

  @Test
  @DisplayName("preserves plain text unchanged")
  void preservesPlainText() {
    String plain = "My Monstera deliciosa, watered every 7 days.";
    assertThat(HtmlSanitizer.sanitize(plain)).isEqualTo(plain);
  }

  @Test
  @DisplayName("preserves unicode and emoji")
  void preservesUnicodeAndEmoji() {
    String withEmoji = "Bureau à côté de la fenêtre 🌿🪴";
    assertThat(HtmlSanitizer.sanitize(withEmoji)).isEqualTo(withEmoji);
  }

  @Test
  @DisplayName("returns null for null input")
  void handlesNull() {
    assertThat(HtmlSanitizer.sanitize(null)).isNull();
  }

  @Test
  @DisplayName("returns empty string for empty input")
  void handlesEmpty() {
    assertThat(HtmlSanitizer.sanitize("")).isEmpty();
  }
}
