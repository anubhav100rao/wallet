package com.wallet.shared.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StreamUtils;

@Configuration
@Profile("local")
@ConditionalOnProperty(
    prefix = "wallet.seed",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LocalSeedDataConfig {

  private static final Logger log = LoggerFactory.getLogger(LocalSeedDataConfig.class);
  private static final String SEED_SCRIPT = "db/seed/local-dev-seed.sql";
  private static final String DEMO_PASSWORD = "password123";

  @Bean
  ApplicationRunner localSeedDataRunner(DataSource dataSource, PasswordEncoder passwordEncoder) {
    return args -> {
      Resource scriptResource = new ClassPathResource(SEED_SCRIPT);
      String script =
          StreamUtils.copyToString(scriptResource.getInputStream(), StandardCharsets.UTF_8)
              .replace("${ALICE_PASSWORD_HASH}", sqlEscaped(passwordEncoder.encode(DEMO_PASSWORD)))
              .replace("${BOB_PASSWORD_HASH}", sqlEscaped(passwordEncoder.encode(DEMO_PASSWORD)))
              .replace(
                  "${CHARLIE_PASSWORD_HASH}", sqlEscaped(passwordEncoder.encode(DEMO_PASSWORD)));

      ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
      populator.addScript(
          new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
              return SEED_SCRIPT;
            }
          });
      populator.execute(dataSource);

      log.info(
          "Loaded local seed data from {}. Demo user password: {}", SEED_SCRIPT, DEMO_PASSWORD);
    };
  }

  private static String sqlEscaped(String value) {
    return value.replace("'", "''");
  }
}
