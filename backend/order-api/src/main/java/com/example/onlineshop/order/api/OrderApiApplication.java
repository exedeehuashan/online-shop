package com.example.onlineshop.order.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Order API", version = "v1",
    description = "Versioned Order API. Path customer IDs and client price snapshots are restricted to development and controlled UAT until authentication and trusted pricing are integrated."))
public class OrderApiApplication {
  public static void main(String[] args) { SpringApplication.run(OrderApiApplication.class, args); }
}
