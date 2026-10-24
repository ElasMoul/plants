package com.plantpal.reminder.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.plantpal.reminder.entity.ReminderChannel;
import com.plantpal.reminder.entity.ReminderPreferences;
import com.plantpal.reminder.service.ReminderDeliveryPolicy;
import com.plantpal.reminder.service.ReminderDeliveryPolicy.Decision;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ReminderDeliveryPolicy - Unit Tests")
class ReminderDeliveryPolicyTest {

  private static final ZoneId UTC = ZoneId.of("UTC");

  private static ZonedDateTime at(int hour, int minute) {
    return ZonedDateTime.of(2026, 10, 24, hour, minute, 0, 0, UTC);
  }

  private static ReminderPreferences prefs(ReminderChannel channel) {
    ReminderPreferences prefs = ReminderPreferences.defaults(1L);
    prefs.setChannel(channel);
    return prefs;
  }

  private static boolean sends(ReminderPreferences prefs, int hour, int minute) {
    return ReminderDeliveryPolicy.decide(prefs, true, at(hour, minute)).any();
  }

  @Nested
  @DisplayName("channel and push subscription")
  class Channels {

    @ParameterizedTest(name = "{0}, subscription={1} -> push={2}, email={3}")
    @CsvSource({
      "PUSH,true,true,false",
      "PUSH,false,false,true",
      "EMAIL,true,false,true",
      "EMAIL,false,false,true",
      "BOTH,true,true,true",
      "BOTH,false,false,true"
    })
    void decidesPerChannel(ReminderChannel channel, boolean hasPush, boolean push, boolean email) {
      Decision decision = ReminderDeliveryPolicy.decide(prefs(channel), hasPush, at(9, 0));

      assertThat(decision).isEqualTo(new Decision(push, email));
    }

    @Test
    @DisplayName("a missing row (defaults) behaves as push only")
    void defaultsArePushOnly() {
      Decision decision =
          ReminderDeliveryPolicy.decide(ReminderPreferences.defaults(1L), true, at(8, 0));

      assertThat(decision).isEqualTo(new Decision(true, false));
    }

    @ParameterizedTest
    @CsvSource({"PUSH,false", "EMAIL,true", "EMAIL,false", "BOTH,true", "BOTH,false"})
    @DisplayName("an unsubscribed user never gets email")
    void unsubscribedGetsNoEmail(ReminderChannel channel, boolean hasPush) {
      ReminderPreferences prefs = prefs(channel);
      prefs.setEmailUnsubscribed(true);

      assertThat(ReminderDeliveryPolicy.decide(prefs, hasPush, at(9, 0)).email()).isFalse();
    }
  }

  @Nested
  @DisplayName("timing")
  class Timing {

    @Test
    @DisplayName("nothing goes out before 08:00 local time")
    void nothingBeforeEight() {
      assertThat(sends(prefs(ReminderChannel.BOTH), 7, 59)).isFalse();
      assertThat(sends(prefs(ReminderChannel.BOTH), 8, 0)).isTrue();
    }

    @Test
    @DisplayName("at most one notification per local day")
    void oncePerLocalDay() {
      ReminderPreferences prefs = prefs(ReminderChannel.BOTH);
      prefs.setLastNotifiedOn(LocalDate.of(2026, 10, 24));

      assertThat(sends(prefs, 12, 0)).isFalse();

      prefs.setLastNotifiedOn(LocalDate.of(2026, 10, 23));
      assertThat(sends(prefs, 12, 0)).isTrue();
    }
  }

  @Nested
  @DisplayName("quiet hours")
  class QuietHours {

    private ReminderPreferences quiet(String start, String end) {
      ReminderPreferences prefs = prefs(ReminderChannel.BOTH);
      prefs.setQuietHoursStart(LocalTime.parse(start));
      prefs.setQuietHoursEnd(LocalTime.parse(end));
      return prefs;
    }

    @ParameterizedTest(name = "13:00-15:00 at {0}:{1} -> quiet={2}")
    @CsvSource({"12,59,false", "13,0,true", "14,59,true", "15,0,false"})
    @DisplayName("same-day window: start inclusive, end exclusive")
    void sameDayWindow(int hour, int minute, boolean quiet) {
      assertThat(sends(quiet("13:00", "15:00"), hour, minute)).isEqualTo(!quiet);
    }

    @ParameterizedTest(name = "22:00-09:00 at {0}:{1} -> quiet={2}")
    @CsvSource({"21,59,false", "22,0,true", "23,30,true", "8,59,true", "9,0,false"})
    @DisplayName("window crossing midnight")
    void midnightWindow(int hour, int minute, boolean quiet) {
      assertThat(sends(quiet("22:00", "09:00"), hour, minute)).isEqualTo(!quiet);
    }

    @Test
    @DisplayName("the midnight-crossing window is quiet just after midnight")
    void quietAfterMidnight() {
      assertThat(
              ReminderDeliveryPolicy.isQuiet(
                  LocalTime.of(22, 0), LocalTime.of(9, 0), LocalTime.of(0, 30)))
          .isTrue();
    }

    @Test
    @DisplayName("equal or missing bounds mean no quiet hours")
    void degenerateWindows() {
      ReminderPreferences half = prefs(ReminderChannel.PUSH);
      half.setQuietHoursStart(LocalTime.of(10, 0));

      assertThat(sends(quiet("10:00", "10:00"), 10, 0)).isTrue();
      assertThat(sends(half, 10, 0)).isTrue();
    }

    @Test
    @DisplayName("a user whose 08:00 is quiet is notified at the first hour after quiet ends")
    void firstHourAfterQuietEnds() {
      ReminderPreferences prefs = quiet("07:00", "10:00");

      assertThat(sends(prefs, 8, 0)).isFalse();
      assertThat(sends(prefs, 9, 0)).isFalse();
      assertThat(sends(prefs, 10, 0)).isTrue();
    }
  }
}
