package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.OrderId;

public record CancelOrderCommand(String customerReference, OrderId orderId, String reason) {}
