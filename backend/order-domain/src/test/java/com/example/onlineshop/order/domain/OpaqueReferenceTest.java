package com.example.onlineshop.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpaqueReferenceTest {
  @Test void customerReferenceRejectsUnicodeWhitespaceAtEitherBoundary() {
    assertThatThrownBy(() -> new CustomerReference("\u2003customer-1"))
        .isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new CustomerReference("customer-1\u00a0"))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test void productReferenceRejectsBoundariesWithoutNormalisingValidValues() {
    assertThatThrownBy(() -> new ProductReference("\u00a0product-1"))
        .isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new ProductReference("product-1\u2003"))
        .isInstanceOf(DomainValidationException.class);

    String exactValue = "product\u2003\u00a0reference";
    assertThat(new ProductReference(exactValue).value()).isSameAs(exactValue);
  }
}
