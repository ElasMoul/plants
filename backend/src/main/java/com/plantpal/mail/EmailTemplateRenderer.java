package com.plantpal.mail;

import com.plantpal.localization.SupportedLanguages;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders the EN/FR templates under {@code classpath:mail/}. Accounts can also be set to a language
 * with no template (e.g. a legacy "ar" value); those fall back to English rather than failing a
 * send.
 */
@Component
public class EmailTemplateRenderer {

  static final String FALLBACK_LANGUAGE = "en";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

  private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

  public RenderedEmail render(EmailTemplate template, String language, Map<String, String> vars) {
    validate(vars);
    String lang = resolveLanguage(language);
    String base = "mail/" + template.resourceBase() + "." + lang;
    String subject = fill(load(base + ".subject.txt"), vars, false).replaceAll("[\\r\\n]+", " ");
    String text = fill(load(base + ".txt"), vars, false);
    String html = fill(load(base + ".html"), vars, true);
    return new RenderedEmail(subject.trim(), text, html);
  }

  static String resolveLanguage(String language) {
    if (language == null || language.length() < 2) {
      return FALLBACK_LANGUAGE;
    }
    String code = language.substring(0, 2).toLowerCase(Locale.ROOT);
    return SupportedLanguages.isSupported(code) ? code : FALLBACK_LANGUAGE;
  }

  private void validate(Map<String, String> vars) {
    for (String required : EmailTemplate.REQUIRED_VARIABLES) {
      String value = vars.get(required);
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Missing email template variable: " + required);
      }
    }
    String link = vars.get("link");
    if (!link.startsWith("https://") && !link.startsWith("http://")) {
      throw new IllegalArgumentException("Email link must be an http(s) URL");
    }
  }

  private String fill(String template, Map<String, String> vars, boolean html) {
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String value = vars.get(matcher.group(1));
      if (value == null) {
        throw new IllegalArgumentException("Missing email template variable: " + matcher.group(1));
      }
      matcher.appendReplacement(
          out, Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(value) : value));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String load(String path) {
    return CACHE.computeIfAbsent(
        path,
        key -> {
          try (var in = new ClassPathResource(key).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
          } catch (IOException e) {
            throw new UncheckedIOException("Email template resource missing: " + key, e);
          }
        });
  }
}
