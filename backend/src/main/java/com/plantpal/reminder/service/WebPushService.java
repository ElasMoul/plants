package com.plantpal.reminder.service;

import com.plantpal.reminder.dto.PushSubscriptionRequest;

public interface WebPushService {

  void saveSubscription(PushSubscriptionRequest request, Long userId);

  void sendNotification(Long userId, String title, String body);
}
