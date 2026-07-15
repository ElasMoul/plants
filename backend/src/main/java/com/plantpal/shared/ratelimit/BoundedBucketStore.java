package com.plantpal.shared.ratelimit;

import io.github.bucket4j.Bucket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A per-key {@link Bucket} cache bounded to a fixed maximum size via LRU eviction.
 *
 * <p>Existing per-user rate limiters in this codebase (ChatServiceImpl, TreatmentServiceImpl,
 * IdentificationServiceImpl) key their {@code Map<Long, Bucket>} by authenticated userId, which is
 * implicitly bounded by the number of real accounts. IP-keyed buckets (T-DEPLOY.3's login/register
 * rate limiting, which must work pre-authentication) have no such natural bound — an attacker can
 * cycle through arbitrary source IPs to grow the map without limit. This wraps a {@link
 * LinkedHashMap} in access-order mode so the least-recently-used entry is evicted once the map
 * exceeds {@code maxEntries}, without pulling in a new caching dependency (Caffeine is not on the
 * classpath — see pom.xml).
 */
public class BoundedBucketStore {

  private final Map<String, Bucket> buckets;

  public BoundedBucketStore(int maxEntries) {
    this.buckets =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > maxEntries;
          }
        };
  }

  /** Thread-safe get-or-create — synchronized because {@link LinkedHashMap} is not. */
  public synchronized Bucket resolveBucket(String key, Supplier<Bucket> bucketSupplier) {
    return buckets.computeIfAbsent(key, k -> bucketSupplier.get());
  }

  /** Test-only visibility into current size, to assert the LRU bound actually holds. */
  synchronized int size() {
    return buckets.size();
  }
}
