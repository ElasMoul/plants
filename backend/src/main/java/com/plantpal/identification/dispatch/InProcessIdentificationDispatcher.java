package com.plantpal.identification.dispatch;

import com.plantpal.identification.event.IdentificationRequestedEvent;
import com.plantpal.identification.service.IdentificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * No-Kafka dispatch strategy (T-DEPLOY.5 — v1.0.0 ships without a Kafka broker in production).
 * Invokes {@link IdentificationService#processIdentification(IdentificationRequestedEvent)}
 * directly, crossing the {@code IdentificationService} bean boundary exactly the way {@code
 * IdentificationConsumer} already does for the Kafka path — that cross-bean call is what makes
 * {@code processIdentification}'s existing {@code @Async("aiTaskExecutor")} annotation take effect
 * (Spring's {@code @Async} proxy has no effect on same-class self-invocation, so this must never be
 * a direct in-class call), so the HTTP thread returns immediately and the 202+poll contract is
 * unchanged, retry/stage-status semantics included.
 *
 * <p>{@code @Lazy} on the constructor parameter breaks what would otherwise be a circular bean
 * dependency: {@code IdentificationServiceImpl} depends on {@link IdentificationDispatcher} (this
 * bean, when {@code transport=in-process}), and this bean in turn depends on {@code
 * IdentificationService} (implemented only by {@code IdentificationServiceImpl}). The lazy proxy
 * defers resolving the real bean until {@link #dispatch} is first called, by which point both beans
 * have finished constructing.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.identification",
    name = "transport",
    havingValue = "in-process")
public class InProcessIdentificationDispatcher implements IdentificationDispatcher {

  private static final Logger log =
      LoggerFactory.getLogger(InProcessIdentificationDispatcher.class);

  private final IdentificationService identificationService;

  public InProcessIdentificationDispatcher(@Lazy IdentificationService identificationService) {
    this.identificationService = identificationService;
  }

  @Override
  public void dispatch(IdentificationRequestedEvent event) {
    log.info(
        "Dispatching identification in-process (no Kafka broker, transport=in-process): id={}",
        event.getIdentificationId());
    identificationService.processIdentification(event);
  }
}
