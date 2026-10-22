package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.OrderId;

public record ConfirmOrderCommand(String customerReference, OrderId orderId) {}
