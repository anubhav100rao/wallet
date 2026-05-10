package com.wallet.shared.idempotency;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {

  @Modifying
  @Query("DELETE FROM IdempotencyKeyEntity i WHERE i.createdAt < :before")
  int deleteOlderThan(Instant before);

  @Modifying
  @Query(
      value =
          "INSERT INTO shared.idempotency_keys (key_id, request_hash, created_at) VALUES (:keyId, :requestHash, NOW()) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertIfNotExists(String keyId, String requestHash);
}
