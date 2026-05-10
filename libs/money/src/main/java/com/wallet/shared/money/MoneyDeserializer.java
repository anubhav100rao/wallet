package com.wallet.shared.money;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Jackson deserializer parsing: {@code {"amount": "100.0000", "currency": "INR"}}.
 *
 * <p>Accepts amount as either a string or a number (for compatibility), but always constructs via
 * {@code new BigDecimal(String)} to avoid floating-point contamination.
 */
public class MoneyDeserializer extends JsonDeserializer<Money> {

  @Override
  public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    JsonNode amountNode = node.get("amount");
    if (amountNode == null) {
      throw new IOException("Missing 'amount' field in Money JSON");
    }

    JsonNode currencyNode = node.get("currency");
    if (currencyNode == null) {
      throw new IOException("Missing 'currency' field in Money JSON");
    }

    // Accept both string and number, but always go through BigDecimal(String)
    String amountStr = amountNode.isTextual() ? amountNode.asText() : amountNode.asText();
    BigDecimal amount = new BigDecimal(amountStr);
    Currency currency = Currency.fromCode(currencyNode.asText());

    return Money.of(amount, currency);
  }
}
