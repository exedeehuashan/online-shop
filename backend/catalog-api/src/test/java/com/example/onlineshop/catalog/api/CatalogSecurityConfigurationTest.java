package com.example.onlineshop.catalog.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import java.time.Duration;

class CatalogSecurityConfigurationTest {
  @Test void failsClosedForMissingSettingsAndUnsafeAlgorithms() {
    CatalogSecurityConfiguration configuration = new CatalogSecurityConfiguration();
    assertThatThrownBy(() -> configuration.catalogJwtDecoder("", "https://issuer.example", "shop-api", "RS256"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> configuration.catalogJwtDecoder(
        "https://issuer.example/jwks", "https://issuer.example", "shop-api", "none"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test void rejectsUndocumentedClockSkew() {
    CatalogSecurityConfiguration configuration = new CatalogSecurityConfiguration();
    assertThatThrownBy(() -> configuration.catalogJwtDecoder(
        "https://issuer.example/jwks", "https://issuer.example", "shop-api", "RS256", Duration.ofSeconds(31)))
        .isInstanceOf(IllegalStateException.class);
  }
}
