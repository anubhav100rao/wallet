package com.wallet.shared.money;

/**
 * ISO 4217 currency codes supported by the wallet platform.
 *
 * <p>Each currency carries its code, display name, and the number of minor units (decimal places).
 * This enum is the single source of truth for supported currencies.
 */
public enum Currency {
  INR("INR", "Indian Rupee", 2),
  USD("USD", "US Dollar", 2),
  EUR("EUR", "Euro", 2),
  GBP("GBP", "British Pound", 2),
  JPY("JPY", "Japanese Yen", 0);

  private final String code;
  private final String displayName;
  private final int minorUnits;

  Currency(String code, String displayName, int minorUnits) {
    this.code = code;
    this.displayName = displayName;
    this.minorUnits = minorUnits;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getMinorUnits() {
    return minorUnits;
  }

  /**
   * Look up a Currency by its ISO 4217 code.
   *
   * @throws IllegalArgumentException if the code is not a supported currency
   */
  public static Currency fromCode(String code) {
    for (Currency c : values()) {
      if (c.code.equals(code)) {
        return c;
      }
    }
    throw new IllegalArgumentException("Unsupported currency code: " + code);
  }
}
