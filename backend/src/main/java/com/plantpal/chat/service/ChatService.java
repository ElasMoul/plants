package com.plantpal.chat.service;

import com.plantpal.chat.dto.ChatRequest;
import com.plantpal.chat.dto.ChatResponse;

public interface ChatService {

  ChatResponse chat(ChatRequest request, Long userId);
}
