package com.plantpal.chat.service;

/**
 * Builds the "user's garden" text block fed into the chat system prompt. Split out of {@code
 * ChatServiceImpl} (T-DEPLOY.2) purely so {@code buildGardenContext} can be {@code @Cacheable}:
 * Spring's caching proxy only intercepts calls that arrive through the bean's external interface,
 * never a same-class self-invocation, so this had to become its own bean rather than a private
 * method (same reasoning already documented for {@code @Async} self-invocation in CLAUDE.md).
 */
public interface GardenContextService {

  /** Cached under {@code "garden"}, keyed by userId, 5-minute TTL (see CacheConfig). */
  String buildGardenContext(Long userId);
}
