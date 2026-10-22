package com.example.onlineshop.catalog.api;
import com.example.onlineshop.catalog.application.*; import com.example.onlineshop.catalog.domain.Product; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.net.URI; import java.time.Instant; import java.util.*; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/products") class ProductController {
 private final CatalogService catalog; ProductController(CatalogService catalog){this.catalog=catalog;}
 @PostMapping ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest r){var p=catalog.create(r.name,r.description,r.price,r.currency,r.stockQuantity);return ResponseEntity.created(URI.create("/api/v1/products/"+p.id())).body(ProductResponse.from(p));}
 @GetMapping("/{id}") ProductResponse get(@PathVariable UUID id){return ProductResponse.from(catalog.get(id));}
 @GetMapping PageResponse list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){var result=catalog.list(page,size);return new PageResponse(result.content().stream().map(ProductResponse::from).toList(),page,size,result.total());}
 record CreateProductRequest(@NotBlank @Size(max=160) String name,@NotNull @Size(max=2000) String description,@NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal price,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@PositiveOrZero int stockQuantity){}
 record ProductResponse(UUID id,String name,String description,String price,String currency,int stockQuantity,Instant createdAt,Instant updatedAt){static ProductResponse from(Product p){return new ProductResponse(p.id(),p.name(),p.description(),p.price().setScale(2).toPlainString(),p.currency(),p.stockQuantity(),p.createdAt(),p.updatedAt());}}
 record PageResponse(List<ProductResponse> content,int page,int size,long totalElements){}
}
