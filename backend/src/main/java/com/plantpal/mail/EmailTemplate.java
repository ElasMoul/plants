package com.plantpal.mail;

import java.util.Set;

/** The emails the application can send. Each maps to {@code mail/<base>.<lang>.*} resources. */
public enum EmailTemplate {
  ACCOUNT_VERIFICATION("verification"),
  ACCOUNT_RECOVERY("recovery"),
  /** Daily reminder digest; also needs {@code reminders} and {@code unsubscribeUrl}. */
  REMINDER_DIGEST("reminder-digest");

  /** Placeholders every template uses; callers must supply all of them. */
  public static final Set<String> REQUIRED_VARIABLES = Set.of("name", "link");

  private final String resourceBase;

  EmailTemplate(String resourceBase) {
    this.resourceBase = resourceBase;
  }

  public String resourceBase() {
    return resourceBase;
  }
}
