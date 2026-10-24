package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plantpal.mail.EmailTemplate;
import com.plantpal.mail.entity.EmailOutbox;
import com.plantpal.mail.entity.EmailStatus;
import com.plantpal.mail.repository.EmailOutboxRepository;
import com.plantpal.reminder.dto.ReminderPreferencesRequest;
import com.plantpal.reminder.dto.ReminderPreferencesResponse;
import com.plantpal.reminder.entity.ReminderChannel;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.repository.ReminderPreferencesRepository;
import com.plantpal.reminder.service.impl.ReminderPreferencesServiceImpl;
import com.plantpal.shared.exception.ValidationException;
import com.plantpal.user.entity.User;
import com.plantpal.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderPreferencesService - Unit Tests")
class ReminderPreferencesServiceTest {

  @Mock private ReminderPreferencesRepository preferencesRepository;
  @Mock private EmailOutboxRepository outboxRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private ReminderPreferencesServiceImpl service;

  @BeforeEach
  void saveEchoesArgument() {
    lenient()
        .when(preferencesRepository.save(any(ReminderPreferences.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  private ReminderPreferencesRequest request(ReminderChannel channel, String start, String end) {
    ReminderPreferencesRequest request = new ReminderPreferencesRequest();
    request.setChannel(channel);
    request.setQuietHoursStart(start);
    request.setQuietHoursEnd(end);
    request.setTimeZone("Europe/Paris");
    return request;
  }

  @Nested
  @DisplayName("get / update")
  class GetAndUpdate {

    @Test
    @DisplayName("get returns the defaults when no row exists")
    void getDefaults() {
      when(preferencesRepository.findById(1L)).thenReturn(Optional.empty());

      ReminderPreferencesResponse response = service.get(1L);

      assertThat(response.channel()).isEqualTo(ReminderChannel.PUSH);
      assertThat(response.quietHoursStart()).isNull();
      assertThat(response.quietHoursEnd()).isNull();
      assertThat(response.timeZone()).isEqualTo("UTC");
      assertThat(response.emailUnsubscribed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ReminderChannel.class)
    @DisplayName("update persists each channel with its quiet hours and zone")
    void persistsEachChannel(ReminderChannel channel) {
      when(preferencesRepository.findById(1L)).thenReturn(Optional.empty());

      ReminderPreferencesResponse response = service.update(1L, request(channel, "22:00", "07:30"));

      assertThat(response.channel()).isEqualTo(channel);
      assertThat(response.quietHoursStart()).isEqualTo("22:00");
      assertThat(response.quietHoursEnd()).isEqualTo("07:30");
      assertThat(response.timeZone()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("a new row gets a random unsubscribe token")
    void newRowGetsToken() {
      when(preferencesRepository.findById(1L)).thenReturn(Optional.empty());

      ReminderPreferences created = service.getOrCreate(1L);

      assertThat(created.getUnsubscribeToken()).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("quiet hours may be cleared by sending both as null")
    void clearsQuietHours() {
      ReminderPreferences existing = ReminderPreferences.defaults(1L);
      existing.setUnsubscribeToken("t");
      existing.setQuietHoursStart(java.time.LocalTime.of(22, 0));
      existing.setQuietHoursEnd(java.time.LocalTime.of(7, 0));
      when(preferencesRepository.findById(1L)).thenReturn(Optional.of(existing));

      ReminderPreferencesResponse response =
          service.update(1L, request(ReminderChannel.PUSH, null, null));

      assertThat(response.quietHoursStart()).isNull();
      assertThat(response.quietHoursEnd()).isNull();
    }

    @Test
    @DisplayName("choosing EMAIL or BOTH re-opts in after an unsubscribe; PUSH does not")
    void optInClearsUnsubscribe() {
      ReminderPreferences existing = ReminderPreferences.defaults(1L);
      existing.setUnsubscribeToken("t");
      existing.setEmailUnsubscribed(true);
      when(preferencesRepository.findById(1L)).thenReturn(Optional.of(existing));

      assertThat(service.update(1L, request(ReminderChannel.PUSH, null, null)).emailUnsubscribed())
          .isTrue();
      assertThat(service.update(1L, request(ReminderChannel.EMAIL, null, null)).emailUnsubscribed())
          .isFalse();
    }

    @Test
    @DisplayName("rejects an unknown time zone")
    void rejectsBadZone() {
      ReminderPreferencesRequest bad = request(ReminderChannel.PUSH, null, null);
      bad.setTimeZone("Mars/Olympus");

      assertThatThrownBy(() -> service.update(1L, bad)).isInstanceOf(ValidationException.class);
      verify(preferencesRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects half-set quiet hours")
    void rejectsHalfSet() {
      assertThatThrownBy(() -> service.update(1L, request(ReminderChannel.PUSH, "22:00", null)))
          .isInstanceOf(ValidationException.class);
      assertThatThrownBy(() -> service.update(1L, request(ReminderChannel.PUSH, null, "07:00")))
          .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects equal quiet-hours bounds")
    void rejectsEqualBounds() {
      assertThatThrownBy(() -> service.update(1L, request(ReminderChannel.PUSH, "22:00", "22:00")))
          .isInstanceOf(ValidationException.class);
    }
  }

  @Nested
  @DisplayName("unsubscribeByToken")
  class Unsubscribe {

    @Test
    @DisplayName("a valid token flags the user and cancels their pending digests")
    void validToken() {
      ReminderPreferences prefs = ReminderPreferences.defaults(1L);
      prefs.setUnsubscribeToken("tok");
      User user = User.builder().email("sara@example.com").build();
      EmailOutbox pending = new EmailOutbox();
      pending.setVariables("{\"name\":\"Sara\"}");
      when(preferencesRepository.findByUnsubscribeToken("tok")).thenReturn(Optional.of(prefs));
      when(userRepository.findById(1L)).thenReturn(Optional.of(user));
      when(outboxRepository.findByTemplateAndRecipientAndStatus(
              EmailTemplate.REMINDER_DIGEST, "sara@example.com", EmailStatus.PENDING))
          .thenReturn(List.of(pending));

      assertThat(service.unsubscribeByToken("tok")).isTrue();

      assertThat(prefs.isEmailUnsubscribed()).isTrue();
      assertThat(pending.getStatus()).isEqualTo(EmailStatus.FAILED);
      assertThat(pending.getLastError()).isEqualTo("unsubscribed");
      assertThat(pending.getVariables()).isEqualTo("{}");
    }

    @Test
    @DisplayName("an unknown token changes nothing")
    void unknownToken() {
      when(preferencesRepository.findByUnsubscribeToken("nope")).thenReturn(Optional.empty());

      assertThat(service.unsubscribeByToken("nope")).isFalse();
      assertThat(service.unsubscribeByToken(" ")).isFalse();
      assertThat(service.unsubscribeByToken(null)).isFalse();
      verify(preferencesRepository, never()).save(any());
    }
  }
}
