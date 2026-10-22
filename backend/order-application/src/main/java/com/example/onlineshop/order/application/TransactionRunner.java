package com.example.onlineshop.order.application;

import java.util.function.Supplier;

@FunctionalInterface
public interface TransactionRunner {
  <T> T execute(Supplier<T> operation);

  static TransactionRunner direct() {
    return new TransactionRunner() {
      @Override public <T> T execute(Supplier<T> operation) { return operation.get(); }
    };
  }
}
