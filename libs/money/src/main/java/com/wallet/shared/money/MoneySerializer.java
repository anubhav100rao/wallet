package com.wallet.shared.money;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Jackson serializer producing: {@code {"amount": "100.0000", "currency": "INR"}}.
 *
 * <p>Amount is serialized as a <strong>string</strong>, not a JSON number, to avoid precision loss
 * in clients that parse JSON numbers as IEEE 754 doubles (e.g., JavaScript).
 */
public class MoneySerializer extends JsonSerializer<Money> {

  @Override
  public void serialize(Money money, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeStringField("amount", money.getAmount().toPlainString());
    gen.writeStringField("currency", money.getCurrency().getCode());
    gen.writeEndObject();
  }
}
