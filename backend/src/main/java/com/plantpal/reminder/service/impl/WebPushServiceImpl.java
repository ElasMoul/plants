package com.plantpal.reminder.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantpal.reminder.dto.PushSubscriptionRequest;
import com.plantpal.reminder.entity.PushSubscription;
import com.plantpal.reminder.repository.PushSubscriptionRepository;
import com.plantpal.reminder.service.WebPushService;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebPushServiceImpl implements WebPushService {

  private static final Logger log = LoggerFactory.getLogger(WebPushServiceImpl.class);

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final ObjectMapper objectMapper;
  private final PushService pushService;

  public WebPushServiceImpl(
      PushSubscriptionRepository pushSubscriptionRepository,
      ObjectMapper objectMapper,
      @Value("${app.web-push.public-key}") String publicKey,
      @Value("${app.web-push.private-key}") String privateKey,
      @Value("${app.web-push.subject}") String subject) {
    this.pushSubscriptionRepository = pushSubscriptionRepository;
    this.objectMapper = objectMapper;
    Security.addProvider(new BouncyCastleProvider());
    try {
      this.pushService = new PushService(publicKey, privateKey, subject);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Invalid VAPID keys configured for web push", e);
    }
  }

  @Override
  @Transactional
  public void saveSubscription(PushSubscriptionRequest request, Long userId) {
    PushSubscription subscription =
        PushSubscription.builder()
            .userId(userId)
            .endpoint(request.getEndpoint())
            .keyP256dh(request.getKeyP256dh())
            .keyAuth(request.getKeyAuth())
            .enabled(true)
            .build();
    pushSubscriptionRepository.save(subscription);
    log.info("Push subscription saved: userId={}", userId);
  }

  @Override
  public void sendNotification(Long userId, String title, String body) {
    List<PushSubscription> subscriptions =
        pushSubscriptionRepository.findByUserIdAndEnabledTrue(userId);
    if (subscriptions.isEmpty()) {
      log.debug("No push subscriptions for userId={}", userId);
      return;
    }
    String payload = buildPayload(title, body);

    for (PushSubscription sub : subscriptions) {
      try {
        Subscription subscription =
            new Subscription(
                sub.getEndpoint(), new Subscription.Keys(sub.getKeyP256dh(), sub.getKeyAuth()));
        Notification notification = new Notification(subscription, payload);
        pushService.send(notification);
        log.debug("Push notification sent: userId={}, subscriptionId={}", userId, sub.getId());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn(
            "Push notification interrupted: userId={}, subscriptionId={}", userId, sub.getId());
      } catch (Exception e) {
        log.warn(
            "Failed to send push notification: userId={}, subscriptionId={}, error={}",
            userId,
            sub.getId(),
            e.getMessage());
      }
    }
  }

  private String buildPayload(String title, String body) {
    try {
      return objectMapper.writeValueAsString(Map.of("title", title, "body", body));
    } catch (JsonProcessingException e) {
      return "{\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
    }
  }
}
