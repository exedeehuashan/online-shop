package com.example.onlineshop.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record PermissionCode(String value) {
  private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)?");
  public PermissionCode {
    if (value == null || !value.equals(value.trim().toLowerCase(Locale.ROOT)) || !FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("permission code must be normalized resource:action");
    }
  }
}
