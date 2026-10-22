package com.plantpal.reminder.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReminderPushTextTest {

  @Test
  void frenchUsersGetFrenchText() {
    assertThat(ReminderPushText.title("fr")).isEqualTo("Rappel d'entretien des plantes");
    assertThat(ReminderPushText.body("fr", 2))
        .isEqualTo("Vous avez 2 plantes à entretenir aujourd'hui");
  }

  @Test
  void everyoneElseGetsEnglish() {
    for (String language : new String[] {"en", null, "ar"}) {
      assertThat(ReminderPushText.title(language)).isEqualTo("Plant care reminder");
      assertThat(ReminderPushText.body(language, 1))
          .isEqualTo("You have 1 plant to care for today");
    }
  }
}
