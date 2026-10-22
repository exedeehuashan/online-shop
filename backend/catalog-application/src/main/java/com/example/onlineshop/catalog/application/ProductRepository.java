package com.example.onlineshop.catalog.application;
import com.example.onlineshop.catalog.domain.Product; import java.util.*;
public interface ProductRepository { Product save(Product product); Optional<Product> find(UUID id); Page findPage(int page,int size); record Page(java.util.List<Product> content,long total){} }
