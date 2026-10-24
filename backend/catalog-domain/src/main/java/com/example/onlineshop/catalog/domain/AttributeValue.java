package com.example.onlineshop.catalog.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record AttributeValue(UUID id, UUID attributeId, AttributeDataType dataType, Object value, String displayValue) {
  public AttributeValue {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(attributeId, "attributeId");
    Objects.requireNonNull(dataType, "dataType");
    Objects.requireNonNull(value, "value");
    boolean valid = switch (dataType) {
      case TEXT -> value instanceof String text && !text.isBlank();
      case INTEGER -> value instanceof Long || value instanceof Integer;
      case DECIMAL -> value instanceof BigDecimal;
      case BOOLEAN -> value instanceof Boolean;
    };
    if (!valid) throw new ProductValidationException("attribute value does not match " + dataType);
    if (displayValue != null && displayValue.length() > 240) throw new ProductValidationException("displayValue is too long");
  }
}
