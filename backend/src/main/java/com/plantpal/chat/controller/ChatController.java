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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "AI plant-care chat assistant")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @Operation(summary = "Send a message to the PlantPal chat assistant")
  @PostMapping
  public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody @Valid ChatRequest request) {
    Long userId = getCurrentUserId();
    ChatResponse response = chatService.chat(request, userId);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long getCurrentUserId() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return user.getId();
  }
}
