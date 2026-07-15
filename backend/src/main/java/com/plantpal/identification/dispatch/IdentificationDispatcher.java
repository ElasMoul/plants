package com.plantpal.identification.dispatch;

import com.plantpal.identification.event.IdentificationRequestedEvent;

/**
 * Hands an {@link IdentificationRequestedEvent} off for asynchronous processing — the seam between
 * {@code IdentificationServiceImpl}'s HTTP-facing {@code submitIdentification}/{@code
 * retryIdentification} methods (which must return a 202-equivalent immediately) and the actual AI
 * pipeline in {@code IdentificationServiceImpl.processIdentification(...)}.
 *
 * <p>Exactly one implementation is active at a time, selected by {@code
 * app.identification.transport} (see {@link com.plantpal.shared.config.KafkaTransportProperties}):
 *
 * <ul>
 *   <li>{@link KafkaIdentificationDispatcher} ({@code transport=kafka}, the default) — publishes to
 *       the {@code identification.requested} topic; {@code IdentificationConsumer} picks it up.
 *   <li>{@link InProcessIdentificationDispatcher} ({@code transport=in-process}, prod/staging per
 *       T-DEPLOY.5) — invokes {@code processIdentification(...)} directly across the {@code
 *       IdentificationService} bean boundary, which is what makes the method's existing
 *       {@code @Async("aiTaskExecutor")} take effect (no broker involved, no HTTP thread blocked).
 * </ul>
 */
public interface IdentificationDispatcher {

  void dispatch(IdentificationRequestedEvent event);
}
