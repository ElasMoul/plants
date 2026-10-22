package com.plantpal.reminder.scheduler;

import com.plantpal.localization.SupportedLanguages;

/** English/French wording of the daily reminder push; unsupported languages get English. */
final class ReminderPushText {

  private ReminderPushText() {}

  static String title(String language) {
    return "fr".equals(SupportedLanguages.normalize(language))
        ? "Rappel d'entretien des plantes"
        : "Plant care reminder";
  }

  static String body(String language, int count) {
    if ("fr".equals(SupportedLanguages.normalize(language))) {
      return "Vous avez "
          + count
          + " plante"
          + (count == 1 ? "" : "s")
          + " à entretenir aujourd'hui";
    }
    return "You have " + count + " plant" + (count == 1 ? "" : "s") + " to care for today";
  }
}
