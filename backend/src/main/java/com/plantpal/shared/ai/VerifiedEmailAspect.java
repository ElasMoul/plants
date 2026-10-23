package com.plantpal.shared.ai;

import com.plantpal.user.service.EmailVerificationService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Refuses unverified users before any quota is reserved or provider called. */
@Aspect
@Component
@Order(-10)
public class VerifiedEmailAspect {
  private final EmailVerificationService verification;

  public VerifiedEmailAspect(EmailVerificationService verification) {
    this.verification = verification;
  }

  @Before("@annotation(gate)")
  public void require(JoinPoint point, RequiresVerifiedEmail gate) {
    verification.requireVerified((Long) point.getArgs()[gate.userArgument()]);
  }
}
