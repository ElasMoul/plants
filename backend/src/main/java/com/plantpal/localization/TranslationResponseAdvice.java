package com.plantpal.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Runs only after domain controllers have enforced ownership. Leaves canonical response data
 * intact.
 */
@RestControllerAdvice(
    basePackages = {
      "com.plantpal.identification.controller",
      "com.plantpal.plant.controller",
      "com.plantpal.species.controller",
      "com.plantpal.treatment.controller",
      "com.plantpal.reminder.controller"
    })
public class TranslationResponseAdvice implements ResponseBodyAdvice<Object> {
  private final ObjectMapper mapper;
  private final TranslationTextExtractor extractor;
  private final TranslationService translations;

  public TranslationResponseAdvice(
      ObjectMapper mapper, TranslationTextExtractor extractor, TranslationService translations) {
    this.mapper = mapper;
    this.extractor = extractor;
    this.translations = translations;
  }

  @Override
  public boolean supports(
      MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter type,
      MediaType media,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    String language = request.getHeaders().getFirst("X-Content-Language");
    if (!(body instanceof ApiResponse<?> api)
        || !api.isSuccess()
        || api.getData() == null
        || !("fr".equals(language) || "ar".equals(language))) return body;
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof User user)) return body;
    ObjectNode envelope = mapper.valueToTree(body);
    try {
      var texts = new java.util.ArrayList<>(extractor.extract(envelope.get("data")));
      boolean shared =
          request.getURI().getPath().matches("/api/v1/species/\\d+(?:/regenerate-description)?");
      texts.addAll(
          translations
              .cached(extractor.names(envelope.get("data")), user.getId(), shared, language)
              .keySet());
      texts = new java.util.ArrayList<>(texts.stream().distinct().sorted().toList());
      if (texts.isEmpty()) return body;
      envelope.set(
          "localization",
          mapper.valueToTree(translations.prepare(texts, user.getId(), shared, language)));
    } catch (RuntimeException unavailable) {
      envelope.set(
          "localization",
          mapper.valueToTree(new TranslationResult("", language, "FAILED", java.util.Map.of())));
    }
    // A translated response must never enter a shared HTTP cache.
    response.getHeaders().setCacheControl("private, no-store");
    return envelope;
  }
}
