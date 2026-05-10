package com.wallet.shared.money;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that Money can be serialized to JSON and deserialized back without loss.
 *
 * <p>Key contract: amount is a <strong>string</strong> in JSON, not a number, to avoid precision
 * loss in clients that use IEEE 754 doubles for JSON numbers.
 */
@DisplayName("Money JSON serialization")
class MoneyJsonTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new MoneyModule());
  }

  @Test
  @DisplayName("serializes to {amount: string, currency: string}")
  void serialize() throws JsonProcessingException {
    Money m = Money.of("100.50", Currency.INR);
    String json = mapper.writeValueAsString(m);
    assertThat(json).isEqualTo("{\"amount\":\"100.5000\",\"currency\":\"INR\"}");
  }

  @Test
  @DisplayName("deserializes from {amount: string, currency: string}")
  void deserialize() throws JsonProcessingException {
    String json = "{\"amount\":\"100.5000\",\"currency\":\"INR\"}";
    Money m = mapper.readValue(json, Money.class);
    assertThat(m.getAmount().toPlainString()).isEqualTo("100.5000");
    assertThat(m.getCurrency()).isEqualTo(Currency.INR);
  }

  @Test
  @DisplayName("round-trip: serialize then deserialize preserves value")
  void roundTrip() throws JsonProcessingException {
    Money original = Money.of("999999999.1234", Currency.USD);
    String json = mapper.writeValueAsString(original);
    Money restored = mapper.readValue(json, Money.class);
    assertThat(restored).isEqualTo(original);
  }

  @Test
  @DisplayName("round-trip with zero")
  void roundTripZero() throws JsonProcessingException {
    Money original = Money.zero(Currency.EUR);
    String json = mapper.writeValueAsString(original);
    Money restored = mapper.readValue(json, Money.class);
    assertThat(restored).isEqualTo(original);
    assertThat(restored.isZero()).isTrue();
  }

  @Test
  @DisplayName("round-trip with negative amount")
  void roundTripNegative() throws JsonProcessingException {
    Money original = Money.of("-42.5678", Currency.GBP);
    String json = mapper.writeValueAsString(original);
    Money restored = mapper.readValue(json, Money.class);
    assertThat(restored).isEqualTo(original);
    assertThat(restored.isNegative()).isTrue();
  }

  @Test
  @DisplayName("deserializes amount given as JSON number (backwards compat)")
  void deserializeNumericAmount() throws JsonProcessingException {
    // Some clients may send numbers instead of strings — we tolerate this
    String json = "{\"amount\": 100.50, \"currency\": \"INR\"}";
    Money m = mapper.readValue(json, Money.class);
    assertThat(m.getAmount()).isEqualByComparingTo("100.5000");
    assertThat(m.getCurrency()).isEqualTo(Currency.INR);
  }
}
