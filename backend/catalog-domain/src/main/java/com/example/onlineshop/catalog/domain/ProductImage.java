package com.example.onlineshop.catalog.domain;
import java.net.URI;import java.util.*;
public record ProductImage(UUID id,UUID productId,UUID skuId,String url,String altText,Integer widthPx,Integer heightPx,int displayOrder,boolean primary,long version){
 public ProductImage(UUID id,UUID productId,UUID skuId,String url,String altText,int displayOrder,boolean primary,long version){this(id,productId,skuId,url,altText,null,null,displayOrder,primary,version);}
 public ProductImage{Objects.requireNonNull(id);Objects.requireNonNull(productId);if(url==null||url.length()>2048||!(url.startsWith("/")&&!url.startsWith("//")||isHttps(url)))throw new ProductValidationException("invalid image URL");if(altText==null||altText.length()>500||displayOrder<0||version<0)throw new ProductValidationException("invalid image metadata");if((widthPx==null)!=(heightPx==null)||(widthPx!=null&&(widthPx<1||widthPx>32768||heightPx<1||heightPx>32768)))throw new ProductValidationException("image dimensions must both be absent or between 1 and 32768");}
 private static boolean isHttps(String value){try{return "https".equals(URI.create(value).getScheme());}catch(IllegalArgumentException e){return false;}}
}
