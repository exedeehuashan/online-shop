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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Reproductions for defects reported in the QA review of PR #19 /
 * PR #19 QA 评审缺陷复现。
 *
 * <p>Each test asserts the contract that the design requires and currently fails.
 * They are disabled so the branch build stays green; remove {@code @Disabled} (or run
 * with {@code -Djunit.jupiter.conditions.deactivate=*}) to reproduce. / 每个用例断言设计
 * 要求的契约，当前实现失败。为保持分支构建为绿已禁用；移除 {@code @Disabled} 即可复现。</p>
 */
class KnownDefectsQaTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);

  /** D1: first ID at the ID epoch with node 0 is 0, violating the "positive 63-bit" contract. */
  @Test @Disabled("D1: TimeOrderedIdGenerator returns 0 at epoch with node 0")
  void d1_firstIdAtEpochMustBeAPositive63BitValue() {
    TimeOrderedIdGenerator generator =
        new TimeOrderedIdGenerator(0, Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));

    long id = generator.nextId();

    assertThat(id).isPositive();
    assertThatCode(() -> new OrderInternalId(id)).doesNotThrowAnyException();
  }

  /** D2: a null unit price surfaces as NullPointerException (HTTP 500) instead of a 422-class domain error. */
  @Test @Disabled("D2: null unit price throws NullPointerException instead of DomainValidationException")
  void d2_nullUnitPriceMustBeASemanticValidationError() {
    CreateOrderHandler handler = new CreateOrderHandler(new NoopOrders(), events -> { }, () -> 100L,
        UUID::randomUUID, CLOCK);
    CreateOrderCommand command = new CreateOrderCommand("customer-1", "Ada", List.of(
        new CreateOrderCommand.Item("product-1", "SKU-1", "Phone", null, 1)),
        new CreateOrderCommand.Address("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null));

    assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(DomainValidationException.class);
  }

  /** D3: opaque references are trimmed, so distinct identifiers collapse to the same owner. */
  @Test @Disabled("D3: opaque customer/product references are trimmed and collide")
  void d3_opaqueReferencesMustPreserveTheirRawValue() {
    assertThat(new CustomerReference(" customer-1 ")).isNotEqualTo(new CustomerReference("customer-1"));
    assertThat(new CustomerReference(" customer-1 ").value()).isEqualTo(" customer-1 ");
    assertThat(new ProductReference("product-1 ").value()).isEqualTo("product-1 ");
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
