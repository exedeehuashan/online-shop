package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.*;
import java.util.*;

public interface OrderRepository {
  Order save(Order order);
  Optional<Order> findByIdAndCustomer(OrderId id, CustomerReference customerReference);
  List<Order> findByCustomer(CustomerReference customerReference, int page, int size);
}
