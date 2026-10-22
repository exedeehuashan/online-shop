package com.example.onlineshop.catalog.api; import com.example.onlineshop.catalog.application.*; import java.time.Clock; import org.springframework.context.annotation.*;
@Configuration class CatalogConfiguration { @Bean Clock clock(){return Clock.systemUTC();} @Bean CatalogService catalog(ProductRepository repository,Clock clock){return new CatalogService(repository,clock);} }
