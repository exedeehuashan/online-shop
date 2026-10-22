package com.example.onlineshop.order.application;

import com.example.onlineshop.order.domain.DomainEvent;
import java.util.List;

public interface DomainEventRepository { void appendAll(List<DomainEvent> events); }
