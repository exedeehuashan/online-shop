package com.example.onlineshop.order.application;

import static org.assertj.core.api.Assertions.*;
import com.example.onlineshop.order.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrderApplicationTest {
  @Test void createHandlerInjectsIdsClockAndPersistsOrderWithEvents() {
    RecordingRepository orders = new RecordingRepository();
    RecordingEvents events = new RecordingEvents();
    Queue<Long> ids = new ArrayDeque<>(List.of(100L, 101L));
    CreateOrderHandler handler = new CreateOrderHandler(orders, events, ids::remove,
        () -> UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC));
    CreateOrderCommand command = new CreateOrderCommand("customer-1", "Ada", List.of(
        new CreateOrderCommand.Item("product-1", "SKU-1", "Phone", new BigDecimal("99.90"), 2)),
        new CreateOrderCommand.Address("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null));
    Order created = handler.handle(command);
    assertThat(created.internalId().value()).isEqualTo(100L);
    assertThat(created.items().get(0).internalId().value()).isEqualTo(101L);
    assertThat(orders.saved).isSameAs(created);
    assertThat(events.saved).singleElement().isInstanceOf(OrderCreated.class);
    assertThat(created.domainEvents()).isEmpty();
  }

  @Test void commandHandlersEnforceOwnershipAndDelegateTransitions() {
    RecordingRepository orders = new RecordingRepository();
    orders.saved = sampleOrder();
    RecordingEvents events = new RecordingEvents();
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T00:00:01Z"), ZoneOffset.UTC);
    Order confirmed = new ConfirmOrderHandler(orders, events, clock).handle(new ConfirmOrderCommand("customer-1", orders.saved.id()));
    assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThatThrownBy(() -> new CancelOrderHandler(orders, events, clock).handle(new CancelOrderCommand("other", orders.saved.id(), "x")))
        .isInstanceOf(OrderNotFoundException.class);
  }

  private static Order sampleOrder() {
    return Order.create(new OrderInternalId(1), new OrderId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
        new CustomerReference("customer-1"), "Ada", List.of(new OrderItem(new OrderItemInternalId(2),
            new ProductReference("product-1"), null, "SKU-1", "Phone", Money.nzd(new BigDecimal("9.00")), 1)),
        new AddressSnapshot("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null), Instant.parse("2026-10-22T00:00:00Z"));
  }

  private static final class RecordingRepository implements OrderRepository {
    private Order saved;
    public Order save(Order order) { saved = order; return order; }
    public Optional<Order> findByIdAndCustomer(OrderId id, CustomerReference customer) {
      return Optional.ofNullable(saved).filter(o -> o.id().equals(id) && o.customerReference().equals(customer));
    }
    public List<Order> findByCustomer(CustomerReference customer, int page, int size) { return List.of(); }
  }
  private static final class RecordingEvents implements DomainEventRepository {
    private List<DomainEvent> saved = List.of();
    public void appendAll(List<DomainEvent> events) { saved = List.copyOf(events); }
  }
}
