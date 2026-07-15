package com.plantpal.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

  @NotBlank
  @Size(max = 2000, message = "Message must be at most 2000 characters")
  @Schema(example = "Why are my monstera's leaves turning yellow?")
  private String message;

  private Long plantId;

  // Prior turns of this session's conversation, oldest first. Optional -- absent/empty means
  // single-turn behavior, byte-for-byte unchanged from before this field existed.
  private List<ChatMessageDto> history;
}
