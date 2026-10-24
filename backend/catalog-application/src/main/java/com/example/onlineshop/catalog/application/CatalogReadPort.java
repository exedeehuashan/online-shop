package com.example.onlineshop.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Read-only public projection port. It deliberately does not expose mutable aggregates. */
public interface CatalogReadPort {
  ProductPage browse(CatalogQuery query);
  Optional<ProductDetail> findById(UUID id);
  Optional<ProductDetail> findBySlug(String slug);

  record CatalogQuery(int page, int size, String sort, List<String> categories,
      Map<String,List<UUID>> attributes, String availability, BigDecimal minPrice,
      BigDecimal maxPrice, String currency) {}
  record Money(String amount, String currency) {}
  record PriceRange(Money min, Money max) {}
  record CategoryRef(UUID id,String slug,String name,UUID parentId,int displayOrder) {}
  record Image(UUID id,UUID skuId,String url,String altText,int width,int height,int displayOrder,boolean primary) {}
  record AttributeDefinition(UUID id,String code,String name,String dataType,String scope,boolean variantAxis) {}
  record AttributeValue(UUID id,Object value,String displayValue) {}
  record AttributeSelection(AttributeDefinition definition,List<AttributeValue> values) {}
  record Sku(UUID id,String sku,boolean isDefault,Money price,int availableQuantity,String availability,
      List<AttributeSelection> attributes,List<UUID> imageIds) {}
  record VariantOption(UUID id,Object value,String displayValue,String availability) {}
  record VariantAxis(AttributeDefinition definition,List<VariantOption> options) {}
  record ProductSummary(UUID id,String slug,String name,String description,UUID defaultSkuId,Money price,
      PriceRange priceRange,String availability,Image primaryImage,List<CategoryRef> categories,Instant updatedAt) {}
  record ProductDetail(UUID id,String slug,String name,String description,UUID defaultSkuId,Money price,
      PriceRange priceRange,String availability,Image primaryImage,List<CategoryRef> categories,Instant createdAt,
      Instant updatedAt,List<AttributeSelection> attributes,List<VariantAxis> variantAxes,List<Sku> skus,List<Image> images) {}
  record PageMetadata(int page,int size,int numberOfElements,long totalElements,int totalPages,boolean first,boolean last) {}
  record FacetBucket(String id,String value,boolean selected,long count) {}
  record AttributeFacet(String code,String name,List<FacetBucket> values) {}
  record PriceFacet(String min,String max,String currency) {}
  record Facets(List<FacetBucket> categories,List<AttributeFacet> attributes,List<FacetBucket> availability,
      PriceFacet price,boolean truncated) {}
  record ProductPage(List<ProductSummary> items,PageMetadata page,Facets facets) {}
}
