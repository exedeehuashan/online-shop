package com.example.onlineshop.order.domain;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {
  private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

  @Test void createsDraftWithImmutableSnapshotsAndCalculatedTotal() {
    Order order = validOrder(List.of(item(11, "PHONE-1", "499.50", 2), item(12, "CASE-1", "20.00", 1)));
    assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
    assertThat(order.total()).isEqualTo(Money.nzd(new BigDecimal("1019.00")));
    assertThat(order.domainEvents()).singleElement().isInstanceOf(OrderCreated.class)
        .extracting("eventType", "eventVersion").containsExactly("OrderCreated", 1);
    assertThatThrownBy(() -> order.items().add(item(13, "X", "1.00", 1))).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test void capturesVersionedCreatedEnvelopeMetadataAndDeepSnapshot() {
    UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID correlationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    List<OrderItem> source = new java.util.ArrayList<>(List.of(item(11, "PHONE-1", "10.00", 2)));
    Order order = Order.create(new OrderInternalId(10), new OrderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
        new CustomerReference("customer-1"), "Ada", source,
        new AddressSnapshot("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null), NOW,
        eventId, correlationId);
    OrderCreated created = (OrderCreated) order.domainEvents().get(0);
    source.clear();
    assertThat(created.eventId()).isEqualTo(eventId);
    assertThat(created.correlationId()).isEqualTo(correlationId);
    assertThat(created.aggregateVersion()).isZero();
    assertThat(created.items()).hasSize(1);
    assertThatThrownBy(() -> created.items().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test void rejectsEmptyOrdersAndDuplicateSkus() {
    assertThatThrownBy(() -> validOrder(List.of())).isInstanceOf(DomainValidationException.class).extracting("code").isEqualTo(DomainErrorCode.EMPTY_ORDER);
    assertThatThrownBy(() -> validOrder(List.of(item(11, "PHONE-1", "1.00", 1), item(12, "PHONE-1", "2.00", 1))))
        .isInstanceOf(DomainValidationException.class).extracting("code").isEqualTo(DomainErrorCode.DUPLICATE_SKU);
  }

  @Test void followsStateMachineAndOnlyEmitsEventsForRealTransitions() {
    Order order = validOrder(List.of(item(11, "PHONE-1", "10.00", 1)));
    order.clearDomainEvents();
    order.confirm(NOW.plusSeconds(1));
    assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.version()).isEqualTo(1);
    assertThat(order.domainEvents()).singleElement().isInstanceOf(OrderConfirmed.class);
    order.clearDomainEvents();
    order.confirm(NOW.plusSeconds(2));
    assertThat(order.domainEvents()).isEmpty();
    assertThat(order.version()).isEqualTo(1);
    order.cancel("Changed mind", NOW.plusSeconds(3));
    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.cancellationReason()).contains("Changed mind");
    assertThat(order.domainEvents()).singleElement().isInstanceOf(OrderCancelled.class);
    order.clearDomainEvents();
    order.cancel("ignored retry value", NOW.plusSeconds(4));
    assertThat(order.domainEvents()).isEmpty();
    assertThatThrownBy(() -> order.confirm(NOW.plusSeconds(5))).isInstanceOf(OrderStateConflictException.class);
  }

  @Test void requiresCancellationReason() {
    Order order = validOrder(List.of(item(11, "PHONE-1", "10.00", 1)));
    assertThatThrownBy(() -> order.cancel("  ", NOW)).isInstanceOf(DomainValidationException.class);
  }

  private static Order validOrder(List<OrderItem> items) {
    return Order.create(new OrderInternalId(10), new OrderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
        new CustomerReference("customer-1"), "Ada Lovelace", items,
        new AddressSnapshot("Ada Lovelace", "1 Queen St", null, "Auckland", "Auckland", "1010", "NZ", null), NOW);
  }

  private static OrderItem item(long id, String sku, String price, int quantity) {
    return new OrderItem(new OrderItemInternalId(id), new ProductReference("product-" + id), null, sku, "Product " + id,
        Money.nzd(new BigDecimal(price)), quantity);
  }
}
