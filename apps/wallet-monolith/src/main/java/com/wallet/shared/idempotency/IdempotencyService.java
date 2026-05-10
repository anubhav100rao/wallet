package com.wallet.shared.idempotency;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

  private final IdempotencyKeyRepository repository;

  public IdempotencyService(IdempotencyKeyRepository repository) {
    this.repository = repository;
  }

  /**
   * Attempts to acquire an idempotency key.
   *
   * <p>Runs in a REQUIRES_NEW transaction so that the lock/key is persisted immediately, even if
   * the outer business transaction fails.
   *
   * @return The existing key if it was already processed or is currently executing, or empty if
   *     this is a new request.
   * @throws IdempotencyKeyMismatchException if the key exists but the payload hash is different.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<IdempotencyKeyEntity> acquire(String keyId, String requestHash) {
    int inserted = repository.insertIfNotExists(keyId, requestHash);

    if (inserted == 1) {
      return Optional.empty(); // New request, go ahead and process
    }

    // If we are here, the key already existed.
    IdempotencyKeyEntity entity =
        repository
            .findById(keyId)
            .orElseThrow(
                () -> new IllegalStateException("Key must exist after ON CONFLICT DO NOTHING"));

    if (!entity.getRequestHash().equals(requestHash)) {
      throw new IdempotencyKeyMismatchException(
          "Idempotency key reused with different payload hash");
    }
    return Optional.of(entity);
  }

  /**
   * Saves the response of a successful execution.
   *
   * <p>Runs in a REQUIRES_NEW transaction to ensure the result is saved regardless of the current
   * thread's transaction state.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(String keyId, int status, String responseBody) {
    repository
        .findById(keyId)
        .ifPresent(
            entity -> {
              entity.complete(status, responseBody);
              repository.save(entity);
            });
  }
}
