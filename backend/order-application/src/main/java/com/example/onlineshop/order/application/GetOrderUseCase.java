package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.Order;

@FunctionalInterface
public interface GetOrderUseCase { Order handle(GetOrderQuery query); }
