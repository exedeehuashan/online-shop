package com.example.onlineshop.catalog.persistence;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="products") class ProductEntity {
 @Id UUID id; @Column(nullable=false,length=160) String name; @Column(nullable=false,length=2000) String description;
 @Column(nullable=false,precision=19,scale=2) BigDecimal price; @Column(nullable=false,length=3) String currency;
 @Column(name="stock_quantity",nullable=false) int stockQuantity; @Column(name="created_at",nullable=false) Instant createdAt; @Column(name="updated_at",nullable=false) Instant updatedAt;
 protected ProductEntity(){} ProductEntity(UUID id,String n,String d,BigDecimal p,String c,int s,Instant ca,Instant ua){this.id=id;name=n;description=d;price=p;currency=c;stockQuantity=s;createdAt=ca;updatedAt=ua;}
}
