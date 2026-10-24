package com.example.onlineshop.catalog.application;
import java.util.List;
public final class InvalidCatalogQueryException extends RuntimeException {
  private final List<Violation> violations;
  public InvalidCatalogQueryException(List<Violation> violations){super("Catalog query is invalid");this.violations=List.copyOf(violations);}
  public List<Violation> violations(){return violations;}
  public record Violation(String field,String message){}
}
