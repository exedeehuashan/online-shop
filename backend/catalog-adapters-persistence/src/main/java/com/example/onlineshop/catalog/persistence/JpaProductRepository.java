package com.example.onlineshop.catalog.persistence;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
interface JpaProductRepository extends JpaRepository<ProductEntity,UUID> {}
