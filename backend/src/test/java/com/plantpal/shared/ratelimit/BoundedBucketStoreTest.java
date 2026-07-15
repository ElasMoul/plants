package com.plantpal.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BoundedBucketStore")
class BoundedBucketStoreTest {

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder().capacity(5).refillIntervally(5, Duration.ofMinutes(1)).build())
        .build();
  }

  @Test
  @DisplayName("resolveBucket() creates a bucket on first access and reuses it on later access")
  void createsAndReusesBucket() {
    BoundedBucketStore store = new BoundedBucketStore(10);
    AtomicInteger creations = new AtomicInteger();

    Bucket first =
        store.resolveBucket(
            "1.2.3.4",
            () -> {
              creations.incrementAndGet();
              return newBucket();
            });
    Bucket second =
        store.resolveBucket(
            "1.2.3.4",
            () -> {
              creations.incrementAndGet();
              return newBucket();
            });

    assertThat(first).isSameAs(second);
    assertThat(creations.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("never grows past maxEntries — the oldest (least-recently-used) key is evicted")
  void staysWithinBound() {
    int maxEntries = 3;
    BoundedBucketStore store = new BoundedBucketStore(maxEntries);

    for (int i = 0; i < 10; i++) {
      String ip = "10.0.0." + i;
      store.resolveBucket(ip, this::newBucket);
      assertThat(store.size()).isLessThanOrEqualTo(maxEntries);
    }

    assertThat(store.size()).isEqualTo(maxEntries);
  }

  @Test
  @DisplayName("evicting the oldest key means it gets a brand-new bucket on next access")
  void evictedKeyGetsFreshBucket() {
    BoundedBucketStore store = new BoundedBucketStore(2);
    AtomicInteger creationsForKeyA = new AtomicInteger();

    store.resolveBucket(
        "A",
        () -> {
          creationsForKeyA.incrementAndGet();
          return newBucket();
        });
    store.resolveBucket("B", this::newBucket);
    // A third distinct key pushes the store past its bound of 2 — "A" (least recently touched)
    // should be evicted.
    store.resolveBucket("C", this::newBucket);

    store.resolveBucket(
        "A",
        () -> {
          creationsForKeyA.incrementAndGet();
          return newBucket();
        });

    assertThat(creationsForKeyA.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("tracks distinct keys independently")
  void tracksKeysIndependently() {
    BoundedBucketStore store = new BoundedBucketStore(10);

    Bucket bucketA = store.resolveBucket("A", this::newBucket);
    Bucket bucketB = store.resolveBucket("B", this::newBucket);

    assertThat(bucketA).isNotSameAs(bucketB);
    assertThat(store.size()).isEqualTo(2);
  }
}
