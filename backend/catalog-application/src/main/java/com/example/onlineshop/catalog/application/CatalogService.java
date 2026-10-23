package com.example.onlineshop.catalog.application;
import com.example.onlineshop.catalog.domain.*; import java.math.BigDecimal; import java.time.*; import java.util.UUID;
public final class CatalogService {
 private final CatalogProductRepository repository; private final Clock clock;
 public CatalogService(CatalogProductRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
 public Product create(String name,String description,BigDecimal price,String currency,int stock){var now=clock.instant();var id=UUID.randomUUID();var aggregate=new CatalogProduct(id,null,slug(name,id),name,description,LifecycleStatus.ACTIVE,null,null,now,now,0);aggregate.addSku(new ProductSku(UUID.randomUUID(),"V1-"+id.toString().replace("-",""),LifecycleStatus.ACTIVE,price,currency,stock,true,0));repository.save(aggregate);return repository.findV1(id).orElseThrow();}
 public Product get(UUID id){return repository.findV1(id).orElseThrow(ProductNotFoundException::new);}
 public ProductRepository.Page list(int page,int size){if(page<0||size<1||size>100)throw new ProductValidationException("page must be >= 0 and size between 1 and 100");return repository.findV1Page(page,size);}
 private static String slug(String name,UUID id){String value=name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");if(value.isBlank())value="product";return value.substring(0,Math.min(value.length(),160))+"-"+id.toString().substring(0,8);}
}
