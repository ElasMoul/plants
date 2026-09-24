package com.plantpal.localization;

import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/content-sections/{kind}/{id}/{section}")
public class SectionTranslationController {
  private final SectionTranslationService service;

  public SectionTranslationController(SectionTranslationService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<SectionTranslationService.View> get(
      @PathVariable String kind,
      @PathVariable Long id,
      @PathVariable String section,
      @AuthenticationPrincipal User user) {
    return ApiResponse.success(service.get(kind, id, section, user.getId()));
  }

  @PostMapping("/translate")
  public ApiResponse<SectionTranslationService.View> translate(
      @PathVariable String kind,
      @PathVariable Long id,
      @PathVariable String section,
      @AuthenticationPrincipal User user) {
    return ApiResponse.success(service.translate(kind, id, section, user.getId()));
  }
}
