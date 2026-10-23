package com.example.onlineshop.catalog.domain;

import java.util.*;

public final class ProductAttribute {
  private final UUID id; private String code; private final String name; private AttributeDataType dataType;
  private final AttributeScope scope; private final boolean variantAxis; private LifecycleStatus status;
  private final List<AttributeValue> values = new ArrayList<>();

  public ProductAttribute(UUID id, String code, String name, AttributeDataType dataType,
                          AttributeScope scope, boolean variantAxis, LifecycleStatus status) {
    this.id = Objects.requireNonNull(id); this.code = normalized(code, 80); this.name = text(name, 120);
    this.dataType = Objects.requireNonNull(dataType); this.scope = Objects.requireNonNull(scope);
    if (variantAxis && !scope.permitsSku()) throw new ProductValidationException("variant axis must permit SKU scope");
    this.variantAxis = variantAxis; this.status = Objects.requireNonNull(status);
  }
  public AttributeValue addValue(UUID id, Object value, String displayValue) {
    status.requireMutable("attribute");
    var candidate = new AttributeValue(id, this.id, dataType, value, displayValue);
    if (values.stream().anyMatch(existing -> existing.value().equals(candidate.value()))) throw new ProductValidationException("duplicate attribute value");
    values.add(candidate); return candidate;
  }
  public void changeIdentity(String code, AttributeDataType type) {
    status.requireMutable("attribute");
    String nextCode = normalized(code, 80); Objects.requireNonNull(type);
    if (!values.isEmpty() && (!this.code.equals(nextCode) || this.dataType != type))
      throw new ProductValidationException("attribute code and type are immutable after use");
    this.code = nextCode; this.dataType = type;
  }
  public void archive() { status = LifecycleStatus.ARCHIVED; }
  public UUID id(){return id;} public String code(){return code;} public String name(){return name;}
  public AttributeDataType dataType(){return dataType;} public AttributeScope scope(){return scope;}
  public boolean variantAxis(){return variantAxis;} public LifecycleStatus status(){return status;}
  public List<AttributeValue> values(){return List.copyOf(values);}
  private static String normalized(String value,int max){return text(value,max).toLowerCase(Locale.ROOT);}
  private static String text(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw new ProductValidationException("invalid text");return value.trim();}
}
