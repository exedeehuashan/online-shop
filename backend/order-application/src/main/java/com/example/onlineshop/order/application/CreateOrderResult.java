package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.Order;

public record CreateOrderResult(Order order, boolean replay) {}
