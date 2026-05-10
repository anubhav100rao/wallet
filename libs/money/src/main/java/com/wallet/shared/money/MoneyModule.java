package com.wallet.shared.money;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson module that registers the Money serializer and deserializer.
 *
 * <p>Usage: register this module on your {@link com.fasterxml.jackson.databind.ObjectMapper} or let
 * Spring Boot auto-detect it via {@code @Bean}.
 */
public class MoneyModule extends SimpleModule {

  public MoneyModule() {
    super("MoneyModule");
    addSerializer(Money.class, new MoneySerializer());
    addDeserializer(Money.class, new MoneyDeserializer());
  }
}
