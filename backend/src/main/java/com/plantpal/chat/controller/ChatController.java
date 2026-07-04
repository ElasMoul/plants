package com.plantpal.chat.controller;

import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;
import com.plantpal.chat.service.ChatService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "AI plant-care chat assistant")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);
  private static final long STREAM_TIMEOUT_MS = 60_000;

  private final ChatService chatService;
  private final Executor aiTaskExecutor;

  public ChatController(
      ChatService chatService, @Qualifier("aiTaskExecutor") Executor aiTaskExecutor) {
    this.chatService = chatService;
    this.aiTaskExecutor = aiTaskExecutor;
  }

  @Operation(summary = "Send a message to the PlantPal chat assistant")
  @PostMapping
  public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody @Valid ChatRequest request) {
    Long userId = getCurrentUserId();
    ChatResponse response = chatService.chat(request, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @Operation(summary = "Send a message to the chat assistant, streaming the reply token-by-token")
  @PostMapping("/stream")
  public SseEmitter chatStream(@RequestBody @Valid ChatRequest request) {
    Long userId = getCurrentUserId();
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

    // Runs off the request thread (same same-class fire-and-forget convention as
    // TreatmentServiceImpl's disease-description generation -- @Async has no effect on
    // self-invocation) so the emitter can be returned immediately and Spring starts flushing
    // chunks as soon as the first one is sent.
    CompletableFuture.runAsync(
        () -> {
          try {
            chatService.chatStream(request, userId, token -> sendToken(emitter, token));
            emitter.complete();
          } catch (Exception e) {
            log.warn("Chat stream failed: userId={}, error={}", userId, e.getMessage());
            emitter.completeWithError(e);
          }
        },
        aiTaskExecutor);

    return emitter;
  }

  private void sendToken(SseEmitter emitter, String token) {
    try {
      emitter.send(token);
    } catch (IOException e) {
      // Client disconnected mid-stream -- nothing more we can do, let the outer catch finish up.
      throw new UncheckedIOException(e);
    }
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
