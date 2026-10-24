package com.plantpal.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

@DisplayName("Reminder digest email template - Unit Tests")
class ReminderDigestTemplateTest {

  private static final String UNSUBSCRIBE_URL =
      "https://www.planotell.com/api/v1/email/unsubscribe?token=abc_DEF-123";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

  private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

  private Map<String, String> vars() {
    return Map.of(
        "name", "Sara",
        "link", "https://www.planotell.com/",
        "reminders", "- Monstera: Watering\n- Ficus: Light",
        "unsubscribeUrl", UNSUBSCRIBE_URL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "fr"})
  @DisplayName("all six resources and the care-type labels exist for the language")
  void resourcesExist(String lang) {
    for (String suffix : new String[] {".subject.txt", ".txt", ".html"}) {
      assertThat(new ClassPathResource("mail/reminder-digest." + lang + suffix).exists())
          .as("reminder-digest.%s%s", lang, suffix)
          .isTrue();
    }
    assertThat(new ClassPathResource("mail/care-type." + lang + ".properties").exists()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "fr"})
  @DisplayName("renders with every placeholder filled and the unsubscribe URL in text and HTML")
  void rendersWithUnsubscribeLink(String lang) {
    RenderedEmail email = renderer.render(EmailTemplate.REMINDER_DIGEST, lang, vars());

    assertThat(email.textBody()).contains(UNSUBSCRIBE_URL).contains("- Monstera: Watering");
    assertThat(email.htmlBody()).contains("href=\"" + UNSUBSCRIBE_URL + "\"");
    assertThat(email.subject()).contains("Planotell").doesNotContain("Monstera");
    assertThat(email.textBody()).doesNotContain("{{");
    assertThat(email.htmlBody()).doesNotContain("{{");
    assertThat(email.htmlBody()).contains("<html lang=\"" + lang + "\">");
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "fr"})
  @DisplayName("only uses placeholders the digest supplies")
  void placeholdersAreKnown(String lang) throws Exception {
    for (String suffix : new String[] {".subject.txt", ".txt", ".html"}) {
      String body =
          new String(
              new ClassPathResource("mail/reminder-digest." + lang + suffix)
                  .getInputStream()
                  .readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8);
      Matcher matcher = PLACEHOLDER.matcher(body);
      while (matcher.find()) {
        assertThat(vars()).containsKey(matcher.group(1));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "fr"})
  @DisplayName("the French and English subjects differ and an Arabic account falls back to English")
  void languages(String lang) {
    String en = renderer.render(EmailTemplate.REMINDER_DIGEST, "en", vars()).subject();
    String other = renderer.render(EmailTemplate.REMINDER_DIGEST, lang, vars()).subject();
    String arabic = renderer.render(EmailTemplate.REMINDER_DIGEST, "ar", vars()).subject();

    assertThat(arabic).isEqualTo(en);
    if (lang.equals("fr")) {
      assertThat(other).isNotEqualTo(en);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "fr"})
  @DisplayName("fails when the unsubscribe URL is missing")
  void requiresUnsubscribeUrl(String lang) {
    Map<String, String> noUnsubscribe =
        Map.of("name", "Sara", "link", "https://www.planotell.com/", "reminders", "- x");

    assertThatThrownBy(() -> renderer.render(EmailTemplate.REMINDER_DIGEST, lang, noUnsubscribe))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsubscribeUrl");
  }
}
