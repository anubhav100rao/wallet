package com.wallet.shared.idempotency;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyPurgeJob {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurgeJob.class);

  private final IdempotencyKeyRepository repository;

  public IdempotencyKeyPurgeJob(IdempotencyKeyRepository repository) {
    this.repository = repository;
  }

  @Scheduled(fixedRateString = "${wallet.idempotency.purge-rate-ms:3600000}") // Every 1 hour
  public void purgeExpiredKeys() {
    Instant before = Instant.now().minus(24, ChronoUnit.HOURS);
    int deleted = repository.deleteOlderThan(before);
    if (deleted > 0) {
      log.info("Purged {} expired idempotency keys (older than {})", deleted, before);
    }
  }
}
