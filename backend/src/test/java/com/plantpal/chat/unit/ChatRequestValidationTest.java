package com.plantpal.chat.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.chat.dto.ChatRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure bean-validation unit test (no Spring context) — asserts the {@code @Size(max = 2000)}
 * constraint added to {@code ChatRequest.message} in T-DEPLOY.3, which the {@code
 * MethodArgumentNotValidException} handler in GlobalExceptionHandler turns into a 400 at the
 * controller boundary.
 */
@DisplayName("ChatRequest — bean validation")
class ChatRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @Test
  @DisplayName("rejects a message longer than 2000 characters")
  void rejectsMessageOverLimit() {
    ChatRequest request = ChatRequest.builder().message("a".repeat(2001)).build();

    Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

    assertThat(violations)
        .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("message"));
  }

  @Test
  @DisplayName("accepts a message exactly at the 2000-character limit")
  void acceptsMessageAtLimit() {
    ChatRequest request = ChatRequest.builder().message("a".repeat(2000)).build();

    Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("rejects a blank message")
  void rejectsBlankMessage() {
    ChatRequest request = ChatRequest.builder().message("   ").build();

    Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("accepts a normal, short message")
  void acceptsNormalMessage() {
    ChatRequest request =
        ChatRequest.builder().message("Why are my leaves turning yellow?").build();

    Set<ConstraintViolation<ChatRequest>> violations = validator.validate(request);

    assertThat(violations).isEmpty();
  }
}
