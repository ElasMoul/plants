package com.plantpal.shared.ai;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiUsage {
  int userArgument();

  boolean scan() default false;
}
