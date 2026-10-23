package com.example.onlineshop.catalog.domain;
public final class OptimisticConflictException extends RuntimeException { public OptimisticConflictException(){super("catalog version conflict");} }
