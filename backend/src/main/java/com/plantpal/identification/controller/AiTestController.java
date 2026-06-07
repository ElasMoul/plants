package com.plantpal.identification.controller;

import com.plantpal.identification.client.OllamaClient;
import com.plantpal.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Tag(name = "AI Test", description = "Dev-only endpoint to verify Ollama connectivity")
public class AiTestController {

  private static final Logger log = LoggerFactory.getLogger(AiTestController.class);

  private final OllamaClient ollamaClient;

  public AiTestController(OllamaClient ollamaClient) {
    this.ollamaClient = ollamaClient;
  }

  @GetMapping("/ai")
  @Operation(summary = "Ping Ollama with a free-text prompt and return its response")
  public ResponseEntity<ApiResponse<String>> testAi(
      @RequestParam(defaultValue = "Say hello and share one plant care tip.") String prompt) {
    log.info("AI connectivity test — prompt: {}", prompt);
    String reply = ollamaClient.chat(prompt);
    return ResponseEntity.ok(ApiResponse.success(reply, "Ollama responded successfully"));
  }
}
