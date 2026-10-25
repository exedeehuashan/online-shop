package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.util.*;

public interface OrderRepository {
  Order save(Order order);
  default Order save(Order order, UUID createdBy) { Order saved = save(order); setCreatedActor(order.internalId(), createdBy); return saved; }
  Optional<Order> findByIdAndCustomer(OrderId id, CustomerReference customerReference);
  List<Order> findByCustomer(CustomerReference customerReference, int page, int size);
  default Optional<Order> findById(OrderId id) { throw new UnsupportedOperationException(); }
  default List<Order> findAll(int page, int size) { throw new UnsupportedOperationException(); }
  default void setCreatedActor(OrderInternalId id, UUID actorId) { throw new UnsupportedOperationException(); }
  default void setUpdatedActor(OrderInternalId id, UUID actorId) { throw new UnsupportedOperationException(); }
}
