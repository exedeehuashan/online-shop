package com.example.onlineshop.catalog.application;
import com.example.onlineshop.catalog.domain.Product; import java.math.BigDecimal; import java.time.*; import java.util.UUID;
public final class CatalogService {
 private final ProductRepository repository; private final Clock clock;
 public CatalogService(ProductRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 public Product create(String name,String description,BigDecimal price,String currency,int stock){var now=clock.instant();return repository.save(new Product(UUID.randomUUID(),name,description,price,currency,stock,now,now));}
 public Product get(UUID id){return repository.find(id).orElseThrow(ProductNotFoundException::new);}
 public ProductRepository.Page list(int page,int size){if(page<0||size<1||size>100)throw new com.example.onlineshop.catalog.domain.ProductValidationException("page must be >= 0 and size between 1 and 100");return repository.findPage(page,size);}
}
