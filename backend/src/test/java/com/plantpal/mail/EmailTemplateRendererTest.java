package com.plantpal.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("EmailTemplateRenderer - Unit Tests")
class EmailTemplateRendererTest {

  private static final String UNSUBSCRIBE_URL =
      "https://www.planotell.com/api/v1/email/unsubscribe?token=abc_DEF-123";
  private static final String LINK = "https://www.planotell.com/verify?token=abc&x=1";

  private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

  private Map<String, String> vars(String name) {
    return Map.of(
        "name",
        name,
        "link",
        LINK,
        "reminders",
        "- Monstera: Watering",
        "unsubscribeUrl",
        UNSUBSCRIBE_URL);
  }

  @Nested
  @DisplayName("language selection")
  class Language {

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    @DisplayName("renders every template in English")
    void rendersEnglish(EmailTemplate template) {
      RenderedEmail email = renderer.render(template, "en", vars("Sara"));

      assertThat(email.subject()).contains("Planotell");
      assertThat(email.textBody()).startsWith("Hi Sara,").contains(LINK);
      assertThat(email.htmlBody()).contains("<html lang=\"en\">");
    }

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    @DisplayName("renders every template in French")
    void rendersFrench(EmailTemplate template) {
      RenderedEmail email = renderer.render(template, "fr", vars("Sara"));

      assertThat(email.textBody()).startsWith("Bonjour Sara,").contains(LINK);
      assertThat(email.htmlBody()).contains("<html lang=\"fr\">");
      assertThat(email.subject())
          .isNotEqualTo(renderer.render(template, "en", vars("Sara")).subject());
    }

    @Test
    @DisplayName("falls back to English for languages without a template")
    void fallsBackToEnglish() {
      RenderedEmail arabic = renderer.render(EmailTemplate.ACCOUNT_RECOVERY, "ar", vars("Sara"));
      RenderedEmail unknown = renderer.render(EmailTemplate.ACCOUNT_RECOVERY, null, vars("Sara"));

      assertThat(arabic.textBody()).startsWith("Hi Sara,");
      assertThat(unknown.textBody()).startsWith("Hi Sara,");
    }

    @Test
    @DisplayName("accepts regional language tags and any letter case")
    void acceptsRegionalTags() {
      assertThat(EmailTemplateRenderer.resolveLanguage("FR-ma")).isEqualTo("fr");
      assertThat(EmailTemplateRenderer.resolveLanguage("x")).isEqualTo("en");
    }
  }

  @Nested
  @DisplayName("safety")
  class Safety {

    @Test
    @DisplayName("escapes user-controlled values in HTML but not in plain text")
    void escapesHtmlOnly() {
      RenderedEmail email =
          renderer.render(EmailTemplate.ACCOUNT_VERIFICATION, "en", vars("<b>Sara</b>"));

      assertThat(email.htmlBody()).contains("&lt;b&gt;Sara&lt;/b&gt;").doesNotContain("<b>Sara");
      assertThat(email.htmlBody()).contains("token=abc&amp;x=1");
      assertThat(email.textBody()).contains("<b>Sara</b>").contains("token=abc&x=1");
    }

    @Test
    @DisplayName("keeps line breaks out of the subject")
    void subjectHasNoLineBreaks() {
      RenderedEmail email =
          renderer.render(EmailTemplate.ACCOUNT_VERIFICATION, "en", vars("a\r\nBcc: x"));

      assertThat(email.subject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    @DisplayName("rejects links that are not http(s)")
    void rejectsNonHttpLinks() {
      Map<String, String> unsafe = Map.of("name", "Sara", "link", "javascript:alert(1)");

      assertThatThrownBy(() -> renderer.render(EmailTemplate.ACCOUNT_RECOVERY, "en", unsafe))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("http(s)");
    }

    @Test
    @DisplayName("rejects a missing or blank required variable")
    void rejectsMissingVariables() {
      Map<String, String> noLink = Map.of("name", "Sara");
      Map<String, String> blankName = Map.of("name", " ", "link", LINK);

      assertThatThrownBy(() -> renderer.render(EmailTemplate.ACCOUNT_RECOVERY, "en", noLink))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("link");
      assertThatThrownBy(() -> renderer.render(EmailTemplate.ACCOUNT_RECOVERY, "en", blankName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("name");
    }
  }
}
