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

  static String requiredOpaque(String value, int maxLength, String field) {
    if (value == null || value.isBlank()) throw invalid(field + " is required");
    if (isUnicodeWhitespace(value.codePointAt(0))
        || isUnicodeWhitespace(value.codePointBefore(value.length()))) {
      throw invalid(field + " must not have leading or trailing whitespace");
    }
    if (value.length() > maxLength) throw invalid(field + " exceeds " + maxLength + " characters");
    return value;
  }

  private static boolean isUnicodeWhitespace(int codePoint) {
    return codePoint >= 0x0009 && codePoint <= 0x000D
        || codePoint == 0x0020
        || codePoint == 0x0085
        || codePoint == 0x00A0
        || codePoint == 0x1680
        || codePoint >= 0x2000 && codePoint <= 0x200A
        || codePoint == 0x2028
        || codePoint == 0x2029
        || codePoint == 0x202F
        || codePoint == 0x205F
        || codePoint == 0x3000;
  }

  static DomainValidationException invalid(String message) {
    return new DomainValidationException(DomainErrorCode.INVALID_VALUE, message);
  }
}
