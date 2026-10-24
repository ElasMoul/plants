package com.plantpal.reminder.controller;

import com.plantpal.reminder.service.ReminderPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) target of the unsubscribe link in reminder emails. The random token is
 * the only credential; an unknown token reveals nothing.
 */
@RestController
@RequestMapping("/api/v1/email/unsubscribe")
@Tag(name = "Notifications", description = "Reminder email unsubscribe")
public class EmailUnsubscribeController {

  static final String CONFIRMATION_PAGE =
      "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
          + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
          + "<meta name=\"robots\" content=\"noindex\"><title>Planotell</title></head>"
          + "<body style=\"font-family:Arial,Helvetica,sans-serif;max-width:480px;"
          + "margin:48px auto;padding:0 16px;color:#1f2a1f;\">"
          + "<h1 style=\"font-size:20px;\">You are unsubscribed</h1>"
          + "<p>You will no longer receive plant care reminders by email. You can turn email"
          + " reminders back on any time in your Planotell preferences.</p>"
          + "<h1 style=\"font-size:20px;\">Vous &ecirc;tes d&eacute;sabonn&eacute;(e)</h1>"
          + "<p>Vous ne recevrez plus de rappels d&rsquo;entretien par e-mail. Vous pouvez les"
          + " r&eacute;activer &agrave; tout moment dans vos pr&eacute;f&eacute;rences"
          + " Planotell.</p>"
          + "</body></html>";

  private static final String NOT_FOUND_PAGE = "<!doctype html><html><body>Not found</body></html>";

  private final ReminderPreferencesService preferencesService;

  public EmailUnsubscribeController(ReminderPreferencesService preferencesService) {
    this.preferencesService = preferencesService;
  }

  @Operation(summary = "Unsubscribe from reminder emails using the token from the email link")
  @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> unsubscribe(@RequestParam("token") String token) {
    if (!preferencesService.unsubscribeByToken(token)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.TEXT_HTML)
          .body(NOT_FOUND_PAGE);
    }
    return ResponseEntity.ok()
        .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
        .header("Cache-Control", "no-store")
        .header("Referrer-Policy", "no-referrer")
        .body(CONFIRMATION_PAGE);
  }
}
