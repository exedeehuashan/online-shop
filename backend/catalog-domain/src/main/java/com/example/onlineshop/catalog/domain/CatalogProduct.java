package com.example.onlineshop.catalog.domain;

import java.time.Instant;
import java.util.*;

public final class CatalogProduct {
  private final UUID id; private final UUID legacyId; private final String slug; private final String name; private final String description;
  private LifecycleStatus status; private final UUID createdBy; private UUID updatedBy; private final Instant createdAt; private Instant updatedAt; private long version;
  private final List<ProductSku> skus = new ArrayList<>(); private final Map<UUID,List<AttributeValue>> attributes=new HashMap<>();
  public CatalogProduct(UUID id,UUID legacyId,String slug,String name,String description,LifecycleStatus status,UUID createdBy,UUID updatedBy,Instant createdAt,Instant updatedAt,long version){
    this.id=Objects.requireNonNull(id);this.legacyId=legacyId;this.slug=text(slug,180);this.name=text(name,160);if(description==null)throw new ProductValidationException("description is required");
    this.description=description;this.status=Objects.requireNonNull(status);this.createdBy=createdBy;this.updatedBy=updatedBy;this.createdAt=Objects.requireNonNull(createdAt);this.updatedAt=Objects.requireNonNull(updatedAt);
    if(updatedAt.isBefore(createdAt)||version<0)throw new ProductValidationException("invalid audit metadata");this.version=version;
  }
  public void addSku(ProductSku sku){status.requireMutable("product");if(skus.stream().anyMatch(s->s.sku().equals(sku.sku())))throw new ProductValidationException("duplicate SKU");if(sku.defaultSku()&&skus.stream().anyMatch(ProductSku::defaultSku))throw new ProductValidationException("only one default SKU is permitted");if(skus.stream().anyMatch(s->s.variantSignature().equals(sku.variantSignature())))throw new ProductValidationException("duplicate variant combination");skus.add(sku);}
  public void assign(ProductAttribute attribute,AttributeValue value){status.requireMutable("product");if(!attribute.scope().permitsProduct())throw new ProductValidationException("attribute does not permit product scope");if(!attribute.id().equals(value.attributeId()))throw new ProductValidationException("attribute value mismatch");attributes.computeIfAbsent(attribute.id(),ignored->new ArrayList<>()).add(value);}
  public ProductSku defaultSku(){return skus.stream().filter(ProductSku::defaultSku).findFirst().orElseThrow(()->new ProductValidationException("product requires a default SKU"));}
  public void archive(UUID actor,Instant now,long expectedVersion){if(version!=expectedVersion)throw new OptimisticConflictException();status=LifecycleStatus.ARCHIVED;updatedBy=actor;updatedAt=Objects.requireNonNull(now);version++;}
  public UUID id(){return id;} public UUID legacyId(){return legacyId;} public String slug(){return slug;} public String name(){return name;} public String description(){return description;} public LifecycleStatus status(){return status;} public UUID createdBy(){return createdBy;} public UUID updatedBy(){return updatedBy;} public Instant createdAt(){return createdAt;} public Instant updatedAt(){return updatedAt;} public long version(){return version;} public List<ProductSku> skus(){return List.copyOf(skus);}
  private static String text(String s,int max){if(s==null||s.isBlank()||s.length()>max)throw new ProductValidationException("invalid product text");return s.trim();}
}
