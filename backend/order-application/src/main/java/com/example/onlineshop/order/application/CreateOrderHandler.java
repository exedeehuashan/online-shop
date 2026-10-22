package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.time.Clock;
import java.util.*;

public final class CreateOrderHandler implements CreateOrderUseCase {
  private final OrderRepository orders;
  private final DomainEventRepository events;
  private final InternalIdGenerator internalIds;
  private final PublicIdGenerator publicIds;
  private final Clock clock;
  private final TransactionRunner transactions;

  public CreateOrderHandler(OrderRepository orders, DomainEventRepository events, InternalIdGenerator internalIds,
                            PublicIdGenerator publicIds, Clock clock) {
    this(orders, events, internalIds, publicIds, clock, TransactionRunner.direct());
  }

  public CreateOrderHandler(OrderRepository orders, DomainEventRepository events, InternalIdGenerator internalIds,
                            PublicIdGenerator publicIds, Clock clock, TransactionRunner transactions) {
    this.orders = Objects.requireNonNull(orders); this.events = Objects.requireNonNull(events);
    this.internalIds = Objects.requireNonNull(internalIds); this.publicIds = Objects.requireNonNull(publicIds);
    this.clock = Objects.requireNonNull(clock);
    this.transactions = Objects.requireNonNull(transactions);
  }

  @Override public Order handle(CreateOrderCommand command) {
    return transactions.execute(() -> create(command));
  }

  private Order create(CreateOrderCommand command) {
    Objects.requireNonNull(command, "command");
    CreateOrderCommand.Address address = Objects.requireNonNull(command.shippingAddress(), "shippingAddress");
    List<CreateOrderCommand.Item> requestedItems = Objects.requireNonNull(command.items(), "items");
    OrderInternalId orderInternalId = new OrderInternalId(internalIds.nextId());
    List<OrderItem> items = requestedItems.stream().map(item -> new OrderItem(
        new OrderItemInternalId(internalIds.nextId()), new ProductReference(item.productReference()), null,
        item.sku(), item.productName(), Money.nzd(item.unitPrice()), item.quantity())).toList();
    Order order = Order.create(orderInternalId, new OrderId(publicIds.nextId()),
        new CustomerReference(command.customerReference()), command.customerName(), items,
        new AddressSnapshot(address.recipient(), address.line1(), address.line2(), address.city(), address.region(),
            address.postalCode(), address.countryCode(), address.phone()), clock.instant());
    Order saved = orders.save(order);
    events.appendAll(order.domainEvents());
    return saved;
  }
}
