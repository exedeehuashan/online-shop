package com.example.onlineshop.order.domain;

final class Validation {
  private Validation() {}

  static String required(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) throw invalid(field + " is required");
    String normalized = value.trim();
    if (normalized.length() > maxLength) throw invalid(field + " exceeds " + maxLength + " characters");
    return normalized;
  }

  static String optional(String value, int maxLength, String field) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isEmpty()) return null;
    if (normalized.length() > maxLength) throw invalid(field + " exceeds " + maxLength + " characters");
    return normalized;
  }

  static DomainValidationException invalid(String message) {
    return new DomainValidationException(DomainErrorCode.INVALID_VALUE, message);
  }
}
