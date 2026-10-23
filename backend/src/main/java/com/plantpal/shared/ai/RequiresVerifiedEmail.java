package com.plantpal.shared.ai;

import java.lang.annotation.*;

/** Marks a service method that starts new AI work and needs a verified email address (PLA-26). */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresVerifiedEmail {
  int userArgument();
}
