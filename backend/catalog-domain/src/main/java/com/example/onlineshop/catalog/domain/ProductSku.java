package com.example.onlineshop.catalog.domain;

import java.math.BigDecimal;
import java.util.*;

public final class ProductSku {
  private final UUID id; private final String sku; private LifecycleStatus status; private BigDecimal price;
  private final String currency; private int availableQuantity; private final boolean defaultSku; private long version;
  private final Map<UUID, AttributeValue> attributes = new HashMap<>();
  public ProductSku(UUID id,String sku,LifecycleStatus status,BigDecimal price,String currency,int quantity,boolean defaultSku,long version){
    this.id=Objects.requireNonNull(id); this.sku=normalizeSku(sku); this.status=Objects.requireNonNull(status);
    this.price=money(price); this.currency=currency(currency); if(quantity<0)throw new ProductValidationException("quantity must be non-negative");
    if(version<0)throw new ProductValidationException("version must be non-negative"); this.availableQuantity=quantity;this.defaultSku=defaultSku;this.version=version;
  }
  public void assign(ProductAttribute attribute,AttributeValue value){status.requireMutable("SKU");if(!attribute.scope().permitsSku())throw new ProductValidationException("attribute does not permit SKU scope");if(!attribute.id().equals(value.attributeId()))throw new ProductValidationException("attribute value mismatch");attributes.put(attribute.id(),value);}
  public String variantSignature(){return attributes.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue().id()).reduce((a,b)->a+";"+b).orElse("default");}
  public void changeStock(int quantity,long expectedVersion){if(quantity<0)throw new ProductValidationException("quantity must be non-negative");mutate(expectedVersion);availableQuantity=quantity;}
  public void changePrice(BigDecimal amount,long expectedVersion){BigDecimal validated=money(amount);mutate(expectedVersion);price=validated;}
  public void archive(long expectedVersion){mutate(expectedVersion);status=LifecycleStatus.ARCHIVED;}
  private void mutate(long expected){status.requireMutable("SKU");if(version!=expected)throw new OptimisticConflictException();version++;}
  public UUID id(){return id;} public String sku(){return sku;} public LifecycleStatus status(){return status;} public BigDecimal price(){return price;} public String currency(){return currency;} public int availableQuantity(){return availableQuantity;} public boolean defaultSku(){return defaultSku;} public long version(){return version;}
  private static String normalizeSku(String s){if(s==null||s.isBlank()||s.length()>100)throw new ProductValidationException("invalid SKU");return s.trim().toUpperCase(Locale.ROOT);}
  private static BigDecimal money(BigDecimal p){if(p==null||p.signum()<0||p.scale()>2||p.precision()>19)throw new ProductValidationException("invalid price");return p;}
  private static String currency(String c){try{String normalized=Currency.getInstance(c).getCurrencyCode();if(!normalized.equals(c))throw new IllegalArgumentException();return normalized;}catch(RuntimeException e){throw new ProductValidationException("invalid currency");}}
}
