package com.plantpal.chat.service;

import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;
import java.util.function.Consumer;

public interface ChatService {

  ChatResponse chat(ChatRequest request, Long userId);

  /**
   * Same context/prompt-building as {@link #chat}, but streamed token-by-token via {@code onToken}.
   * Runs synchronously on the calling thread — the caller is responsible for running it off the
   * HTTP request thread (e.g. via an executor) so the response can flush incrementally. Throws
   * {@code PlantPalException} (429) if the rate limit is exceeded, before any streaming starts.
   */
  void chatStream(ChatRequest request, Long userId, Consumer<String> onToken);
}
