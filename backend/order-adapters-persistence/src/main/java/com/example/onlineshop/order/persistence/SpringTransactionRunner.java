package com.example.onlineshop.order.persistence;

import com.example.onlineshop.order.application.TransactionRunner;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringTransactionRunner implements TransactionRunner {
  private final TransactionTemplate transactions;
  public SpringTransactionRunner(PlatformTransactionManager manager) {
    this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
  }
  @Override public <T> T execute(Supplier<T> operation) {
    return transactions.execute(status -> operation.get());
  }
}
