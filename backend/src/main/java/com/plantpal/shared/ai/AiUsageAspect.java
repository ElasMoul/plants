package com.plantpal.shared.ai;

import com.plantpal.user.service.UsageService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Counts accepted AI attempts, including failed provider calls, across all app instances. */
@Aspect
@Component
@Order(0)
public class AiUsageAspect {
  private final UsageService usage;

  public AiUsageAspect(UsageService usage) {
    this.usage = usage;
  }

  @Before("@annotation(budget)")
  public void reserve(JoinPoint point, AiUsage budget) {
    usage.consumeAi((Long) point.getArgs()[budget.userArgument()], budget.scan());
  }
}
