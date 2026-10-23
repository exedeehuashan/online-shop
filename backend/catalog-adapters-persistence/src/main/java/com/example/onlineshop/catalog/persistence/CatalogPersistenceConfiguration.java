package com.example.onlineshop.catalog.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackageClasses = ProductEntity.class)
@EnableJpaRepositories(basePackageClasses = JpaProductRepository.class)
public class CatalogPersistenceConfiguration {
}
