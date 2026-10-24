package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.reminder.entity.CareType;
import com.plantpal.reminder.entity.Reminder;
import com.plantpal.reminder.entity.ReminderChannel;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.repository.PushSubscriptionRepository;
import com.plantpal.reminder.repository.ReminderPreferencesRepository;
import com.plantpal.reminder.repository.ReminderRepository;
import com.plantpal.reminder.scheduler.ReminderScheduler;
import com.plantpal.reminder.service.ReminderDigestMailer;
import com.plantpal.reminder.service.ReminderPreferencesService;
import com.plantpal.reminder.service.WebPushService;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderScheduler - Unit Tests")
class ReminderSchedulerTest {

  private static final Instant NINE_UTC = Instant.parse("2026-10-24T09:00:00Z");

  @Mock private ReminderRepository reminderRepository;
  @Mock private WebPushService webPushService;
  @Mock private PushSubscriptionRepository pushSubscriptionRepository;
  @Mock private ReminderPreferencesRepository preferencesRepository;
  @Mock private ReminderPreferencesService preferencesService;
  @Mock private UserRepository userRepository;
  @Mock private ReminderDigestMailer digestMailer;
  @Mock private TransactionTemplate tx;

  private User user;
  private ReminderPreferences stored;

  @BeforeEach
  void setUp() {
    user = User.builder().email("sara@example.com").firstName("Sara").build();
    user.setId(1L);
    stored = ReminderPreferences.defaults(1L);
    stored.setUnsubscribeToken("tok");
    lenient()
        .doAnswer(
            inv -> {
              inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
    lenient().when(preferencesService.getOrCreate(1L)).thenReturn(stored);
    lenient().when(userRepository.findAllById(any())).thenReturn(List.of(user));
    lenient().when(reminderRepository.findAllDue(any())).thenReturn(List.of(due(1L)));
  }

  private ReminderScheduler scheduler(Instant now) {
    return new ReminderScheduler(
        reminderRepository,
        webPushService,
        pushSubscriptionRepository,
        preferencesRepository,
        preferencesService,
        userRepository,
        digestMailer,
        tx,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private static Reminder due(Long userId) {
    return Reminder.builder().plantId(5L).userId(userId).careType(CareType.WATERING).build();
  }

  private ReminderPreferences prefs(ReminderChannel channel, String zone) {
    ReminderPreferences prefs = ReminderPreferences.defaults(1L);
    prefs.setChannel(channel);
    prefs.setTimeZone(zone);
    prefs.setUnsubscribeToken("tok");
    when(preferencesRepository.findAllById(any())).thenReturn(List.of(prefs));
    return prefs;
  }

  private void subscribed(boolean hasPush) {
    when(pushSubscriptionRepository.existsByUserIdAndEnabledTrue(1L)).thenReturn(hasPush);
  }

  private void verifyNothingSent() {
    verify(digestMailer, never()).enqueue(any(), anyList(), anyString());
    verify(webPushService, never()).sendNotification(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("a user with no preferences row and a subscription gets push only")
  void defaultsPushOnly() {
    when(preferencesRepository.findAllById(any())).thenReturn(List.of());
    subscribed(true);

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verify(webPushService).sendNotification(eq(1L), anyString(), anyString());
    verify(digestMailer, never()).enqueue(any(), anyList(), anyString());
    assertThat(stored.getLastNotifiedOn()).isEqualTo(LocalDate.of(2026, 10, 24));
  }

  @Test
  @DisplayName("a push user with no subscription gets the email digest instead")
  void fallbackDigest() {
    when(preferencesRepository.findAllById(any())).thenReturn(List.of());
    subscribed(false);

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verify(digestMailer).enqueue(eq(user), anyList(), eq("tok"));
    verify(webPushService, never()).sendNotification(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("EMAIL sends email only, even with a push subscription")
  void emailOnly() {
    prefs(ReminderChannel.EMAIL, "UTC");
    subscribed(true);

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verify(digestMailer).enqueue(eq(user), anyList(), eq("tok"));
    verify(webPushService, never()).sendNotification(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("BOTH sends push and email")
  void both() {
    prefs(ReminderChannel.BOTH, "UTC");
    subscribed(true);

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verify(digestMailer).enqueue(eq(user), anyList(), eq("tok"));
    verify(webPushService).sendNotification(eq(1L), anyString(), anyString());
  }

  @Test
  @DisplayName("a second run on the same local day sends nothing")
  void secondRunSendsNothing() {
    ReminderPreferences prefs = prefs(ReminderChannel.BOTH, "UTC");
    prefs.setLastNotifiedOn(LocalDate.of(2026, 10, 24));
    subscribed(true);

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verifyNothingSent();
  }

  @Test
  @DisplayName("the stamp from a first run stops the next hour's run")
  void stampStopsNextRun() {
    subscribed(false);
    // The scheduler stamps the stored row; reuse it as the prefs the next run reads.
    stored.setChannel(ReminderChannel.EMAIL);
    when(preferencesRepository.findAllById(any())).thenReturn(List.of(stored));

    scheduler(NINE_UTC).sendDueReminderNotifications();
    scheduler(NINE_UTC.plusSeconds(3600)).sendDueReminderNotifications();

    verify(digestMailer).enqueue(eq(user), anyList(), eq("tok"));
  }

  @Test
  @DisplayName("nothing is sent before 08:00 in the user's time zone")
  void respectsUserZone() {
    // 09:00 UTC is 05:00 in New York (EDT, UTC-4); 12:00 UTC is 08:00.
    prefs(ReminderChannel.EMAIL, "America/New_York");
    subscribed(false);

    scheduler(NINE_UTC).sendDueReminderNotifications();
    verify(digestMailer, never()).enqueue(any(), anyList(), anyString());

    scheduler(Instant.parse("2026-10-24T12:00:00Z")).sendDueReminderNotifications();
    verify(digestMailer).enqueue(eq(user), anyList(), eq("tok"));
  }

  @Test
  @DisplayName("nothing is sent inside quiet hours; it goes out the first hour after")
  void quietHours() {
    ReminderPreferences prefs = prefs(ReminderChannel.BOTH, "Europe/Paris");
    prefs.setQuietHoursStart(LocalTime.of(8, 0));
    prefs.setQuietHoursEnd(LocalTime.of(11, 0));
    subscribed(true);

    // 08:00 UTC = 10:00 CEST (Paris, UTC+2): quiet.
    scheduler(Instant.parse("2026-10-24T08:00:00Z")).sendDueReminderNotifications();
    verifyNothingSent();

    // 09:00 UTC = 11:00 CEST: quiet just ended.
    scheduler(NINE_UTC).sendDueReminderNotifications();
    verify(digestMailer).enqueue(eq(user), anyList(), eq("tok"));
    verify(webPushService).sendNotification(eq(1L), anyString(), anyString());
  }

  @Test
  @DisplayName("an unsubscribed EMAIL user gets nothing")
  void unsubscribedGetsNothing() {
    ReminderPreferences prefs = prefs(ReminderChannel.EMAIL, "UTC");
    prefs.setEmailUnsubscribed(true);
    subscribed(false);

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verifyNothingSent();
  }

  @Test
  @DisplayName("one user's failure does not stop the others")
  void failureIsolated() {
    User other = User.builder().email("b@example.com").firstName("B").build();
    other.setId(2L);
    when(reminderRepository.findAllDue(any())).thenReturn(List.of(due(1L), due(2L)));
    when(userRepository.findAllById(any())).thenReturn(List.of(user, other));
    when(preferencesRepository.findAllById(any())).thenReturn(List.of());
    when(pushSubscriptionRepository.existsByUserIdAndEnabledTrue(any())).thenReturn(true);
    when(preferencesService.getOrCreate(2L)).thenReturn(ReminderPreferences.defaults(2L));
    doThrow(new IllegalStateException("push down"))
        .when(webPushService)
        .sendNotification(eq(1L), anyString(), anyString());

    scheduler(NINE_UTC).sendDueReminderNotifications();

    verify(webPushService).sendNotification(eq(2L), anyString(), anyString());
  }
}
