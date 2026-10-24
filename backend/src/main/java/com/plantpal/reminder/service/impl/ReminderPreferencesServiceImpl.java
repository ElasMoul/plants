package com.plantpal.reminder.service.impl;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.repository.EmailOutboxRepository;
import com.plantpal.reminder.dto.ReminderPreferencesRequest;
import com.plantpal.reminder.dto.ReminderPreferencesResponse;
import com.plantpal.reminder.entity.ReminderChannel;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.repository.ReminderPreferencesRepository;
import com.plantpal.reminder.service.ReminderPreferencesService;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.repository.UserRepository;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderPreferencesServiceImpl implements ReminderPreferencesService {

  private static final Logger log = LoggerFactory.getLogger(ReminderPreferencesServiceImpl.class);
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;
  private static final String UNSUBSCRIBED_ERROR = "unsubscribed";

  private final ReminderPreferencesRepository preferencesRepository;
  private final EmailOutboxRepository outboxRepository;
  private final UserRepository userRepository;

  public ReminderPreferencesServiceImpl(
      ReminderPreferencesRepository preferencesRepository,
      EmailOutboxRepository outboxRepository,
      UserRepository userRepository) {
    this.preferencesRepository = preferencesRepository;
    this.outboxRepository = outboxRepository;
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public ReminderPreferencesResponse get(Long userId) {
    return toResponse(
        preferencesRepository
            .findById(userId)
            .orElseGet(() -> ReminderPreferences.defaults(userId)));
  }

  @Override
  @Transactional
  public ReminderPreferencesResponse update(
      Long userId, @Valid ReminderPreferencesRequest request) {
    ZoneId zone = parseZone(request.getTimeZone());
    LocalTime start = parseTime(request.getQuietHoursStart());
    LocalTime end = parseTime(request.getQuietHoursEnd());
    if ((start == null) != (end == null)) {
      throw new ValidationException("Quiet hours need both a start and an end");
    }
    if (start != null && start.equals(end)) {
      throw new ValidationException("Quiet hours start and end must differ");
    }
    ReminderPreferences prefs = loadOrCreate(userId);
    prefs.setChannel(request.getChannel());
    prefs.setQuietHoursStart(start);
    prefs.setQuietHoursEnd(end);
    prefs.setTimeZone(zone.getId());
    if (request.getChannel() != ReminderChannel.PUSH) {
      prefs.setEmailUnsubscribed(false);
    }
    return toResponse(preferencesRepository.save(prefs));
  }

  @Override
  @Transactional
  public ReminderPreferences getOrCreate(Long userId) {
    return loadOrCreate(userId);
  }

  private ReminderPreferences loadOrCreate(Long userId) {
    return preferencesRepository
        .findById(userId)
        .orElseGet(
            () -> {
              ReminderPreferences prefs = ReminderPreferences.defaults(userId);
              prefs.setUnsubscribeToken(newToken());
              return preferencesRepository.save(prefs);
            });
  }

  @Override
  @Transactional
  public boolean unsubscribeByToken(String token) {
    if (token == null || token.isBlank()) {
      return false;
    }
    return preferencesRepository
        .findByUnsubscribeToken(token)
        .map(
            prefs -> {
              prefs.setEmailUnsubscribed(true);
              preferencesRepository.save(prefs);
              userRepository
                  .findById(prefs.getUserId())
                  .ifPresent(user -> cancelPendingDigests(user.getEmail()));
              return true;
            })
        .orElse(false);
  }

  private void cancelPendingDigests(String email) {
    for (EmailOutbox row :
        outboxRepository.findByTemplateAndRecipientAndStatus(
            EmailTemplate.REMINDER_DIGEST, email, EmailStatus.PENDING)) {
      row.setStatus(EmailStatus.FAILED);
      row.setLastError(UNSUBSCRIBED_ERROR);
      row.setVariables("{}");
    }
    log.info("Reminder email unsubscribe applied");
  }

  private String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static ZoneId parseZone(String value) {
    try {
      return ZoneId.of(value);
    } catch (DateTimeException e) {
      log.debug("Rejected time zone '{}': {}", value, e.getMessage());
      throw new ValidationException("Unknown time zone: " + value);
    }
  }

  private static LocalTime parseTime(String value) {
    return value == null ? null : LocalTime.parse(value, HH_MM);
  }

  private static ReminderPreferencesResponse toResponse(ReminderPreferences prefs) {
    return new ReminderPreferencesResponse(
        prefs.getChannel(),
        prefs.getQuietHoursStart() == null ? null : prefs.getQuietHoursStart().format(HH_MM),
        prefs.getQuietHoursEnd() == null ? null : prefs.getQuietHoursEnd().format(HH_MM),
        prefs.getTimeZone(),
        prefs.isEmailUnsubscribed());
  }
}
