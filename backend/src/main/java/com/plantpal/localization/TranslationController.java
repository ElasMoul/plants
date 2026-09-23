package com.plantpal.localization;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/translations")
public class TranslationController {
  private final TranslationService service;

  public TranslationController(TranslationService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public ApiResponse<TranslationResult> get(
      @PathVariable String id, @AuthenticationPrincipal User user) {
    return ApiResponse.success(service.get(id, user.getId()));
  }

  @PostMapping("/{id}/retry")
  public ApiResponse<TranslationResult> retry(
      @PathVariable String id, @AuthenticationPrincipal User user) {
    return ApiResponse.success(service.retry(id, user.getId()));
  }
}
