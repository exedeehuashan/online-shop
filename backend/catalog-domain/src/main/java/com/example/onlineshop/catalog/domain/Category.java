package com.example.onlineshop.catalog.domain;
import java.util.*;
public record Category(UUID id,String slug,String name,UUID parentId,LifecycleStatus status,int displayOrder,long version){public Category{Objects.requireNonNull(id);slug=text(slug,180);name=text(name,160);Objects.requireNonNull(status);if(id.equals(parentId)||displayOrder<0||version<0)throw new ProductValidationException("invalid category");}private static String text(String v,int max){if(v==null||v.isBlank()||v.length()>max)throw new ProductValidationException("invalid category text");return v.trim();}}
