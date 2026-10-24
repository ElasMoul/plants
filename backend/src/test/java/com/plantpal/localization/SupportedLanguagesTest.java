package com.plantpal.localization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportedLanguagesTest {

  @Test
  void listsExactlyEnglishAndFrenchWithEnglishDefault() {
    assertThat(SupportedLanguages.CODES).containsExactly("en", "fr");
    assertThat(SupportedLanguages.DEFAULT).isEqualTo("en");
  }

  @Test
  void normalizesMissingAndUnsupportedValuesToEnglish() {
    assertThat(SupportedLanguages.normalize(null)).isEqualTo("en");
    assertThat(SupportedLanguages.normalize(" ")).isEqualTo("en");
    assertThat(SupportedLanguages.normalize("ar")).isEqualTo("en");
    assertThat(SupportedLanguages.normalize("fr")).isEqualTo("fr");
    assertThat(SupportedLanguages.normalize("FR")).isEqualTo("fr");
  }

  @Test
  void displayNameFollowsTheNormalizedLanguage() {
    assertThat(SupportedLanguages.displayName("fr")).isEqualTo("French");
    assertThat(SupportedLanguages.displayName("ar")).isEqualTo("English");
    assertThat(SupportedLanguages.displayName(null)).isEqualTo("English");
  }
}
