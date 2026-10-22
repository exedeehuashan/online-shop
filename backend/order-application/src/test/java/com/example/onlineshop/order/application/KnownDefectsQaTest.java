package com.example.onlineshop.order.application;

import static org.assertj.core.api.Assertions.*;

import com.example.onlineshop.order.domain.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reproductions for defects reported in the QA review of PR #19 /
 * PR #19 QA 评审缺陷复现。
 *
 * <p>Permanent regression coverage for defects found by independent QA. / 独立 QA
 * 发现缺陷的常规回归覆盖。</p>
 */
class KnownDefectsQaTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);

  /** D1: first ID at the ID epoch with node 0 is 0, violating the "positive 63-bit" contract. */
  @Test
  void d1_firstIdAtEpochMustBeAPositive63BitValue() {
    TimeOrderedIdGenerator generator =
        new TimeOrderedIdGenerator(0, Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));

    long id = generator.nextId();

    assertThat(id).isPositive();
    assertThat(id).isLessThanOrEqualTo(Long.MAX_VALUE);
    assertThatCode(() -> new OrderInternalId(id)).doesNotThrowAnyException();
    assertThat(generator.nextId()).isGreaterThan(id);
  }

  /** D2: a null unit price surfaces as NullPointerException (HTTP 500) instead of a 422-class domain error. */
  @Test
  void d2_nullUnitPriceMustBeASemanticValidationError() {
    CreateOrderHandler handler = new CreateOrderHandler(new NoopOrders(), events -> { }, () -> 100L,
        UUID::randomUUID, CLOCK);
    CreateOrderCommand command = new CreateOrderCommand("customer-1", "Ada", List.of(
        new CreateOrderCommand.Item("product-1", "SKU-1", "Phone", null, 1)),
        new CreateOrderCommand.Address("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null));

    assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainValidationException.class)
        .extracting("code").isEqualTo(DomainErrorCode.INVALID_VALUE);

    assertThatThrownBy(() -> new CreateOrderCommand("customer-1", "Ada",
        java.util.Arrays.asList((CreateOrderCommand.Item) null), command.shippingAddress()))
        .isInstanceOf(DomainValidationException.class)
        .extracting("code").isEqualTo(DomainErrorCode.INVALID_VALUE);
  }

  /** D3: whitespace around opaque identifiers is rejected instead of silently normalized. */
  @Test
  void d3_opaqueReferencesMustExplicitlyRejectSurroundingWhitespace() {
    assertThatThrownBy(() -> new CustomerReference(" customer-1 "))
        .isInstanceOf(DomainValidationException.class).hasMessageContaining("whitespace");
    assertThatThrownBy(() -> new ProductReference("product-1 "))
        .isInstanceOf(DomainValidationException.class).hasMessageContaining("whitespace");
    assertThat(new CustomerReference("customer-1").value()).isEqualTo("customer-1");
  }

  private static final class NoopOrders implements OrderRepository {
    @Override public Order save(Order order) { return order; }
    @Override public Optional<Order> findByIdAndCustomer(OrderId id, CustomerReference customer) {
      return Optional.empty();
    }
    @Override public List<Order> findByCustomer(CustomerReference customer, int page, int size) {
      return List.of();
    }
  }
}
