package com.example.onlineshop.catalog.persistence;
import com.example.onlineshop.catalog.application.ProductRepository; import com.example.onlineshop.catalog.domain.Product; import java.util.*; import org.springframework.data.domain.*; import org.springframework.stereotype.Repository;
@Repository public class PostgresProductRepository implements ProductRepository {
 private final JpaProductRepository jpa; public PostgresProductRepository(JpaProductRepository jpa){this.jpa=jpa;}
 public Product save(Product p){return map(jpa.save(entity(p)));} public Optional<Product> find(UUID id){return jpa.findById(id).map(this::map);}
 public Page findPage(int page,int size){var result=jpa.findAll(PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id"))));return new Page(result.stream().map(this::map).toList(),result.getTotalElements());}
 private ProductEntity entity(Product p){return new ProductEntity(p.id(),p.name(),p.description(),p.price(),p.currency(),p.stockQuantity(),p.createdAt(),p.updatedAt());}
 private Product map(ProductEntity e){return new Product(e.id,e.name,e.description,e.price,e.currency,e.stockQuantity,e.createdAt,e.updatedAt);}
}
