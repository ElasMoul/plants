package com.plantpal.chat.dto;

import jakarta.validation.constraints.NotBlank;
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

  @NotBlank private String message;

  private Long plantId;

  // Prior turns of this session's conversation, oldest first. Optional -- absent/empty means
  // single-turn behavior, byte-for-byte unchanged from before this field existed.
  private List<ChatMessageDto> history;
}
