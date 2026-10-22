package com.plantpal.journal.controller;

import com.plantpal.journal.dto.JournalEventRequest;
import com.plantpal.journal.dto.JournalEventResponse;
import com.plantpal.journal.service.JournalEventService;
import com.plantpal.shared.dto.ApiResponse;
import com.plantpal.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plants/{plantId}/journal-events")
@Tag(name = "Plant journal", description = "Events in a plant's life")
@SecurityRequirement(name = "bearerAuth")
public class JournalEventController {

  private final JournalEventService journalEventService;

  public JournalEventController(JournalEventService journalEventService) {
    this.journalEventService = journalEventService;
  }

  @Operation(summary = "List a plant's journal events, oldest first")
  @GetMapping
  public ResponseEntity<ApiResponse<Page<JournalEventResponse>>> list(
      @PathVariable Long plantId,
      @AuthenticationPrincipal User user,
      @PageableDefault(size = 100, sort = "eventDate", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(journalEventService.listEvents(plantId, user.getId(), pageable)));
  }

  @Operation(summary = "Add a journal event to a plant")
  @PostMapping
  public ResponseEntity<ApiResponse<JournalEventResponse>> create(
      @PathVariable Long plantId,
      @AuthenticationPrincipal User user,
      @Valid @RequestBody JournalEventRequest request) {
    JournalEventResponse response = journalEventService.createEvent(plantId, request, user.getId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Journal event added"));
  }

  @Operation(summary = "Replace a journal event")
  @PutMapping("/{eventId}")
  public ResponseEntity<ApiResponse<JournalEventResponse>> update(
      @PathVariable Long plantId,
      @PathVariable Long eventId,
      @AuthenticationPrincipal User user,
      @Valid @RequestBody JournalEventRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            journalEventService.updateEvent(plantId, eventId, request, user.getId()),
            "Journal event updated"));
  }

  @Operation(summary = "Delete a journal event")
  @DeleteMapping("/{eventId}")
  public ResponseEntity<Void> delete(
      @PathVariable Long plantId, @PathVariable Long eventId, @AuthenticationPrincipal User user) {
    journalEventService.deleteEvent(plantId, eventId, user.getId());
    return ResponseEntity.noContent().build();
  }

  /** An unknown event type or a malformed date is a client error, not a server error. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
      HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("Invalid event type or date", HttpStatus.BAD_REQUEST.value()));
  }
}
