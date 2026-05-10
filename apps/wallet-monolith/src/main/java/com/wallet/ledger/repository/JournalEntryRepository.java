package com.wallet.ledger.repository;

import com.wallet.ledger.domain.JournalEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
  List<JournalEntry> findByTransactionId(UUID transactionId);

  @Query(
      "SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END FROM JournalEntry j WHERE j.transactionId = :transactionId")
  boolean existsByTransactionId(UUID transactionId);
}
