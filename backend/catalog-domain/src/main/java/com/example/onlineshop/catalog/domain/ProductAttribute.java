package com.example.onlineshop.catalog.domain;

import java.util.*;

public final class ProductAttribute {
  private final UUID id; private String code; private final String name; private AttributeDataType dataType;
  private final AttributeScope scope; private final boolean variantAxis; private final boolean filterable;
  private final boolean required; private final String validationJson; private final int displayOrder; private LifecycleStatus status;
  private final List<AttributeValue> values = new ArrayList<>();

  public ProductAttribute(UUID id, String code, String name, AttributeDataType dataType,
                          AttributeScope scope, boolean variantAxis, LifecycleStatus status) {
    this(id,code,name,dataType,scope,variantAxis,false,false,null,0,status);
  }
  public ProductAttribute(UUID id,String code,String name,AttributeDataType dataType,AttributeScope scope,
                          boolean variantAxis,boolean filterable,boolean required,String validationJson,int displayOrder,LifecycleStatus status) {
    this.id = Objects.requireNonNull(id); this.code = normalized(code, 80); this.name = text(name, 120);
    this.dataType = Objects.requireNonNull(dataType); this.scope = Objects.requireNonNull(scope);
    if (variantAxis && !scope.permitsSku()) throw new ProductValidationException("variant axis must permit SKU scope");
    if(displayOrder<0)throw new ProductValidationException("invalid display order");
    this.variantAxis=variantAxis;this.filterable=filterable;this.required=required;this.validationJson=validationJson;this.displayOrder=displayOrder;this.status=Objects.requireNonNull(status);
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
  public void requireAssignable(){if(status!=LifecycleStatus.ACTIVE)throw new ProductValidationException("attribute is not active");}
  public UUID id(){return id;} public String code(){return code;} public String name(){return name;}
  public AttributeDataType dataType(){return dataType;} public AttributeScope scope(){return scope;}
  public boolean variantAxis(){return variantAxis;} public LifecycleStatus status(){return status;}
  public boolean filterable(){return filterable;} public boolean required(){return required;} public String validationJson(){return validationJson;} public int displayOrder(){return displayOrder;}
  public List<AttributeValue> values(){return List.copyOf(values);}
  private static String normalized(String value,int max){return text(value,max).toLowerCase(Locale.ROOT);}
  private static String text(String value,int max){if(value==null||value.isBlank()||value.length()>max)throw new ProductValidationException("invalid text");return value.trim();}
}
