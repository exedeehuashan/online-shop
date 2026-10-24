package com.example.onlineshop.catalog.application;
import com.example.onlineshop.catalog.domain.*;import java.time.Instant;import java.util.*;
public interface CatalogProductRepository {CatalogProduct save(CatalogProduct product);Optional<CatalogProduct> findAggregate(UUID id);ProductAttribute save(ProductAttribute attribute,UUID actor,Instant now);Category save(Category category,UUID actor,Instant now);ProductImage save(ProductImage image,UUID actor,Instant now);Optional<Product> findV1(UUID id);ProductRepository.Page findV1Page(int page,int size);}
