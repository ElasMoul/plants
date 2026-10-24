package com.plantpal.reminder.dto;

import com.plantpal.reminder.entity.ReminderChannel;

public record ReminderPreferencesResponse(
    ReminderChannel channel,
    String quietHoursStart,
    String quietHoursEnd,
    String timeZone,
    boolean emailUnsubscribed) {}
