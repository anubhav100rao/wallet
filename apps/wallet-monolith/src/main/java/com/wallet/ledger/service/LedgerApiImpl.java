package com.wallet.ledger.service;

import com.wallet.ledger.api.LedgerApi;
import com.wallet.ledger.service.LedgerService.PostEntryCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerApiImpl implements LedgerApi {

  private final LedgerService ledgerService;

  public LedgerApiImpl(LedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @Override
  public void post(UUID transactionId, List<LedgerEntry> entries, String metadata) {
    List<PostEntryCommand> commands =
        entries.stream()
            .map(e -> new PostEntryCommand(e.accountId(), e.amount(), e.currency()))
            .toList();
    ledgerService.post(transactionId, commands, metadata);
  }
}
