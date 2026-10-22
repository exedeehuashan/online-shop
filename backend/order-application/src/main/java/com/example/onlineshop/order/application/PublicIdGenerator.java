package com.example.onlineshop.order.application;

import java.util.UUID;

@FunctionalInterface
public interface PublicIdGenerator { UUID nextId(); }
