package com.plantpal.mail.controller;

import com.plantpal.mail.dto.EmailDeliveryView;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.service.MailService;
import com.plantpal.shared.dto.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets the operator see queued, sent and failed emails. Admin-only via {@code /api/v1/admin}. */
@RestController
@RequestMapping("/api/v1/admin/email-deliveries")
public class AdminEmailController {

  private static final int MAX_PAGE_SIZE = 100;

  private final MailService mail;

  public AdminEmailController(MailService mail) {
    this.mail = mail;
  }

  /** {@code ?status=FAILED} lists the emails that gave up after every retry. */
  @GetMapping
  public ApiResponse<Page<EmailDeliveryView>> deliveries(
      @RequestParam(required = false) EmailStatus status, Pageable pageable) {
    Pageable bounded =
        PageRequest.of(
            pageable.getPageNumber(),
            Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    return ApiResponse.success(mail.deliveries(status, bounded));
  }
}
