package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.Order;
import java.util.List;

@FunctionalInterface
public interface ListCustomerOrdersUseCase { List<Order> handle(ListCustomerOrdersQuery query); }
