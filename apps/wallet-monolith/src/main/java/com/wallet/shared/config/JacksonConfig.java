package com.wallet.shared.config;

import com.wallet.shared.money.MoneyModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Money Jackson module for automatic serialization/deserialization. */
@Configuration
public class JacksonConfig {

  @Bean
  public MoneyModule moneyModule() {
    return new MoneyModule();
  }
}
