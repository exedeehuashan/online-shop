package com.example.onlineshop.order.domain;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test void calculatesUsingFixedNzdScale() {
    Money price = Money.nzd(new BigDecimal("12.30"));
    assertThat(price.multiply(3)).isEqualTo(Money.nzd(new BigDecimal("36.90")));
    assertThat(price.add(Money.nzd(new BigDecimal("0.70")))).isEqualTo(Money.nzd(new BigDecimal("13.00")));
  }

  @Test void rejectsNegativeOrFractionalCentAmounts() {
    assertThatThrownBy(() -> Money.nzd(new BigDecimal("-0.01"))).isInstanceOf(DomainValidationException.class).hasMessageContaining("non-negative");
    assertThatThrownBy(() -> Money.nzd(new BigDecimal("1.001"))).isInstanceOf(DomainValidationException.class).hasMessageContaining("two decimal");
  }
}
