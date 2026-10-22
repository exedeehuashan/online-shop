package com.example.onlineshop.order.application;

import static org.assertj.core.api.Assertions.*;
import com.example.onlineshop.order.domain.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Independent QA coverage for the Issue #4 application boundary / Issue #4 应用层边界独立 QA 覆盖。
 *
 * <p>Exercises the full create -> get -> confirm -> cancel flow against an in-memory
 * adapter, plus ownership, pagination validation, duplicate-SKU short-circuiting and
 * transaction-port wiring. / 使用内存适配器覆盖完整用例链路、所有权、分页校验、重复 SKU
 * 短路与事务端口编排。</p>
 */
class OrderApplicationQaTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);
  private static final UUID PUBLIC_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @Test void createGetConfirmCancelFlowWorksThroughPorts() {
    InMemoryOrders orders = new InMemoryOrders();
    RecordingEvents events = new RecordingEvents();
    AtomicInteger sequence = new AtomicInteger(100);
    CreateOrderHandler create = new CreateOrderHandler(orders, events, sequence::incrementAndGet,
        () -> PUBLIC_ID, CLOCK);

    Order created = create.handle(command("customer-1", "SKU-1"));
    assertThat(created.status()).isEqualTo(OrderStatus.DRAFT);
    Order fetched = new GetOrderHandler(orders).handle(new GetOrderQuery("customer-1", created.id()));
    assertThat(fetched.total()).isEqualTo(Money.nzd(new BigDecimal("199.80")));
    Order confirmed = new ConfirmOrderHandler(orders, events, CLOCK)
        .handle(new ConfirmOrderCommand("customer-1", created.id()));
    assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
    Order cancelled = new CancelOrderHandler(orders, events, CLOCK)
        .handle(new CancelOrderCommand("customer-1", created.id(), "changed mind"));

    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(cancelled.version()).isEqualTo(2);
    assertThat(orders.saved).hasSize(1);
    assertThat(events.types()).containsExactly("OrderCreated", "OrderConfirmed", "OrderCancelled");
  }

  @Test void getAndListHideOrdersOwnedByAnotherCustomer() {
    InMemoryOrders orders = new InMemoryOrders();
    RecordingEvents events = new RecordingEvents();
    AtomicInteger sequence = new AtomicInteger(100);
    CreateOrderHandler create = new CreateOrderHandler(orders, events, sequence::incrementAndGet,
        () -> PUBLIC_ID, CLOCK);
    Order created = create.handle(command("customer-1", "SKU-1"));

    assertThatThrownBy(() -> new GetOrderHandler(orders)
        .handle(new GetOrderQuery("customer-2", created.id())))
        .isInstanceOf(OrderNotFoundException.class);
    assertThat(new ListCustomerOrdersHandler(orders)
        .handle(new ListCustomerOrdersQuery("customer-2", 0, 20))).isEmpty();
  }

  @Test void listHandlerReturnsImmutableSnapshotAndForwardsPagination() {
    InMemoryOrders orders = new InMemoryOrders();
    RecordingEvents events = new RecordingEvents();
    AtomicInteger sequence = new AtomicInteger(100);
    CreateOrderHandler create = new CreateOrderHandler(orders, events, sequence::incrementAndGet,
        () -> PUBLIC_ID, CLOCK);
    create.handle(command("customer-1", "SKU-1"));

    List<Order> page = new ListCustomerOrdersHandler(orders)
        .handle(new ListCustomerOrdersQuery("customer-1", 0, 20));

    assertThat(page).hasSize(1);
    assertThatThrownBy(() -> page.add(page.get(0))).isInstanceOf(UnsupportedOperationException.class);
    assertThat(orders.lastPage).isZero();
    assertThat(orders.lastSize).isEqualTo(20);
  }

  @Test void listQueryRejectsOutOfRangePagination() {
    assertThatThrownBy(() -> new ListCustomerOrdersQuery("customer-1", -1, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListCustomerOrdersQuery("customer-1", 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListCustomerOrdersQuery("customer-1", 0, 101))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void duplicateSkuIsRejectedBeforeAnythingIsPersisted() {
    InMemoryOrders orders = new InMemoryOrders();
    RecordingEvents events = new RecordingEvents();
    CreateOrderHandler create = new CreateOrderHandler(orders, events, new AtomicInteger(100)::incrementAndGet,
        () -> PUBLIC_ID, CLOCK);
    CreateOrderCommand duplicate = new CreateOrderCommand("customer-1", "Ada", List.of(
        new CreateOrderCommand.Item("product-1", "SKU-1", "Phone", new BigDecimal("10.00"), 1),
        new CreateOrderCommand.Item("product-2", "SKU-1", "Phone", new BigDecimal("10.00"), 1)),
        address());

    assertThatThrownBy(() -> create.handle(duplicate)).isInstanceOf(DomainValidationException.class)
        .extracting("code").isEqualTo(DomainErrorCode.DUPLICATE_SKU);
    assertThat(orders.saved).isEmpty();
    assertThat(events.types()).isEmpty();
  }

  @Test void confirmReplayDoesNotSaveOrAppendASecondEvent() {
    InMemoryOrders orders = new InMemoryOrders();
    RecordingEvents events = new RecordingEvents();
    AtomicInteger sequence = new AtomicInteger(100);
    Order created = new CreateOrderHandler(orders, events, sequence::incrementAndGet, () -> PUBLIC_ID, CLOCK)
        .handle(command("customer-1", "SKU-1"));
    ConfirmOrderHandler confirm = new ConfirmOrderHandler(orders, events, CLOCK);
    confirm.handle(new ConfirmOrderCommand("customer-1", created.id()));
    int savesAfterFirst = orders.saveCount;

    Order replay = confirm.handle(new ConfirmOrderCommand("customer-1", created.id()));

    assertThat(replay.status()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(orders.saveCount).isEqualTo(savesAfterFirst);
    assertThat(events.types()).containsExactly("OrderCreated", "OrderConfirmed");
  }

  @Test void mutationHandlersUseTheInjectedTransactionRunner() {
    InMemoryOrders orders = new InMemoryOrders();
    RecordingEvents events = new RecordingEvents();
    AtomicInteger sequence = new AtomicInteger(100);
    CreateOrderHandler create = new CreateOrderHandler(orders, events, sequence::incrementAndGet, () -> PUBLIC_ID, CLOCK);
    AtomicInteger transactions = new AtomicInteger();
    TransactionRunner runner = new TransactionRunner() {
      @Override public <T> T execute(java.util.function.Supplier<T> operation) {
        transactions.incrementAndGet();
        return operation.get();
      }
    };
    Order created = create.handle(command("customer-1", "SKU-1"));

    new ConfirmOrderHandler(orders, events, CLOCK, runner)
        .handle(new ConfirmOrderCommand("customer-1", created.id()));
    new CancelOrderHandler(orders, events, CLOCK, runner)
        .handle(new CancelOrderCommand("customer-1", created.id(), "changed mind"));

    assertThat(transactions).hasValue(2);
  }

  private static CreateOrderCommand command(String customer, String sku) {
    return new CreateOrderCommand(customer, "Ada", List.of(
        new CreateOrderCommand.Item("product-1", sku, "Phone", new BigDecimal("99.90"), 2)), address());
  }

  private static CreateOrderCommand.Address address() {
    return new CreateOrderCommand.Address("Ada", "1 Queen St", null, "Auckland", null, "1010", "NZ", null);
  }

  private static final class InMemoryOrders implements OrderRepository {
    private final Map<UUID, Order> saved = new LinkedHashMap<>();
    private int saveCount;
    private int lastPage = -1;
    private int lastSize = -1;

    @Override public Order save(Order order) {
      saveCount++;
      saved.put(order.id().value(), order);
      return order;
    }

    @Override public Optional<Order> findByIdAndCustomer(OrderId id, CustomerReference customer) {
      return Optional.ofNullable(saved.get(id.value()))
          .filter(order -> order.customerReference().equals(customer));
    }

    @Override public List<Order> findByCustomer(CustomerReference customer, int page, int size) {
      lastPage = page;
      lastSize = size;
      return saved.values().stream().filter(order -> order.customerReference().equals(customer)).toList();
    }
  }

  private static final class RecordingEvents implements DomainEventRepository {
    private final List<DomainEvent> events = new ArrayList<>();

    @Override public void appendAll(List<DomainEvent> batch) {
      events.addAll(batch);
    }

    private List<String> types() {
      return events.stream().map(DomainEvent::eventType).toList();
    }
  }
}
