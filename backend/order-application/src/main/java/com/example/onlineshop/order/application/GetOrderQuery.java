package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.OrderId;

public record GetOrderQuery(String customerReference, OrderId orderId) {}
