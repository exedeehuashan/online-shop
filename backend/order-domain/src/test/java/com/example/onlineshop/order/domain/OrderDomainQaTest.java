package com.example.onlineshop.order.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Independent QA coverage for Issue #4 / Issue #4 独立 QA 覆盖。
 *
 * <p>Focuses on invariants and state transitions that the developer suite does not
 * assert directly: DRAFT cancellation, line-total consistency, positive internal IDs
 * and multi-line totals. / 聚焦开发者测试未直接断言的领域不变量与状态转换。</p>
 */
class OrderDomainQaTest {
  private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

  @Test void draftOrderCanBeCancelledDirectly() {
    Order order = order(item(11, "PHONE-1", "10.00", 1));
    order.clearDomainEvents();

    order.cancel("changed mind", NOW.plusSeconds(1));

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.version()).isEqualTo(1);
    assertThat(order.cancellationReason()).contains("changed mind");
    assertThat(order.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    assertThat(order.domainEvents()).singleElement().isInstanceOf(OrderCancelled.class)
        .satisfies(event -> assertThat(((OrderCancelled) event).reason()).isEqualTo("changed mind"));
  }

  @Test void confirmedOrderCanStillBeCancelledOnceAndOnlyEmitsOneEvent() {
    Order order = order(item(11, "PHONE-1", "10.00", 1));
    order.confirm(NOW.plusSeconds(1));
    order.clearDomainEvents();

    order.cancel("out of stock", NOW.plusSeconds(2));
    order.cancel("retry ignored", NOW.plusSeconds(3));

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.version()).isEqualTo(2);
    assertThat(order.cancellationReason()).contains("out of stock");
    assertThat(order.domainEvents()).singleElement().isInstanceOf(OrderCancelled.class);
  }

  @Test void rejectsLineTotalThatDoesNotMatchPriceTimesQuantity() {
    assertThatThrownBy(() -> new OrderItem(new OrderItemInternalId(11), new ProductReference("product-11"), null,
        "PHONE-1", "Phone", Money.nzd(new BigDecimal("10.00")), 2, Money.nzd(new BigDecimal("19.99"))))
        .isInstanceOf(DomainValidationException.class)
        .hasMessageContaining("line total");
  }

  @Test void rejectsNonPositiveInternalIds() {
    assertThatThrownBy(() -> new OrderInternalId(0)).isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new OrderInternalId(-1)).isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new OrderItemInternalId(0)).isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new OrderItemInternalId(-1)).isInstanceOf(DomainValidationException.class);
  }

  @Test void totalIsExactSumOfLineTotalsAtFixedScale() {
    Order order = order(
        item(11, "PHONE-1", "499.50", 2),
        item(12, "CASE-1", "20.00", 1),
        item(13, "CABLE-1", "12.35", 3));

    assertThat(order.total()).isEqualTo(Money.nzd(new BigDecimal("1056.05")));
    assertThat(order.items()).hasSize(3);
    assertThat(order.total().amount().scale()).isEqualTo(2);
  }

  @Test void duplicateSkuCheckIsAppliedAfterNormalisation() {
    assertThatThrownBy(() -> order(item(11, "PHONE-1", "1.00", 1), item(12, " PHONE-1 ", "2.00", 1)))
        .isInstanceOf(DomainValidationException.class)
        .extracting("code").isEqualTo(DomainErrorCode.DUPLICATE_SKU);
  }

  private static Order order(OrderItem... items) {
    return Order.create(new OrderInternalId(10),
        new OrderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
        new CustomerReference("customer-1"), "Ada Lovelace", List.of(items),
        new AddressSnapshot("Ada Lovelace", "1 Queen St", null, "Auckland", "Auckland", "1010", "NZ", null), NOW);
  }

  private static OrderItem item(long id, String sku, String price, int quantity) {
    return new OrderItem(new OrderItemInternalId(id), new ProductReference("product-" + id), null, sku,
        "Product " + id, Money.nzd(new BigDecimal(price)), quantity);
  }
}
