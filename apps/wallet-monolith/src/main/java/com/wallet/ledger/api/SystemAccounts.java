package com.wallet.ledger.api;

import com.wallet.shared.money.Currency;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the deterministic SYSTEM_CASH ledger account UUID for a given currency.
 *
 * <p>Account rows are seeded by Flyway migration V8; this class only mirrors those IDs in code so
 * sagas can reference them without a database round-trip.
 */
public final class SystemAccounts {

  private static final Map<Currency, UUID> SYSTEM_CASH = new EnumMap<>(Currency.class);

  static {
    SYSTEM_CASH.put(Currency.INR, UUID.fromString("00000000-0000-0000-0000-000000494e52"));
    SYSTEM_CASH.put(Currency.USD, UUID.fromString("00000000-0000-0000-0000-000000555344"));
    SYSTEM_CASH.put(Currency.EUR, UUID.fromString("00000000-0000-0000-0000-000000455552"));
    SYSTEM_CASH.put(Currency.GBP, UUID.fromString("00000000-0000-0000-0000-000000474250"));
    SYSTEM_CASH.put(Currency.JPY, UUID.fromString("00000000-0000-0000-0000-0000004a5059"));
  }

  private SystemAccounts() {}

  public static UUID systemCash(String currencyCode) {
    return systemCash(Currency.fromCode(currencyCode));
  }

  public static UUID systemCash(Currency currency) {
    UUID id = SYSTEM_CASH.get(currency);
    if (id == null) {
      throw new IllegalArgumentException("No SYSTEM_CASH account for currency " + currency);
    }
    return id;
  }
}
