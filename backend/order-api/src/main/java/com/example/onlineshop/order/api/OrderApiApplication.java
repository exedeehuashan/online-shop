package com.example.onlineshop.order.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Order API", version = "v2",
    description = "Authenticated Order API with principal-derived v2 ownership and frozen v1 compatibility."),
    security = @SecurityRequirement(name="oidcBearer"))
@SecurityScheme(name="oidcBearer",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
public class OrderApiApplication {
  public static void main(String[] args) { SpringApplication.run(OrderApiApplication.class, args); }
}
