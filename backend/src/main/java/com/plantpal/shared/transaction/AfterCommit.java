package com.plantpal.shared.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers work that another thread will perform against rows written by the current transaction.
 *
 * <p>Launching an async task (or an in-process dispatch) inside a {@code @Transactional} method
 * races the commit: the task can miss the uncommitted row, or finish first and then have its result
 * overwritten when the caller's stale entity snapshot is flushed on commit. Wrapping the launch in
 * {@link #run} starts it only once the transaction has committed (never on rollback), and
 * immediately when no transaction is active.
 */
public final class AfterCommit {

  private AfterCommit() {}

  public static void run(Runnable task) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      task.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            task.run();
          }
        });
  }
}
