package com.example.onlineshop.identity.api;

import com.example.onlineshop.identity.application.IdentityBootstrap;
import com.example.onlineshop.identity.persistence.JdbcIdentityBootstrap;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.onlineshop.identity.persistence.JdbcRbacAssignments;

@SpringBootApplication(scanBasePackages = "com.example.onlineshop.identity")
public class IdentityApiApplication {
  public static void main(String[] args) { SpringApplication.run(IdentityApiApplication.class, args); }

  @Bean IdentityBootstrap identityBootstrap(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
    return new JdbcIdentityBootstrap(jdbc, new TransactionTemplate(transactions));
  }

  @Bean JdbcRbacAssignments rbacAssignments(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
    return new JdbcRbacAssignments(jdbc, new TransactionTemplate(transactions));
  }

  @Bean ApplicationRunner configuredBootstrap(
      IdentityBootstrap bootstrap,
      @Value("${identity.bootstrap.enabled:false}") boolean enabled,
      @Value("${identity.bootstrap.issuer:}") String issuer,
      @Value("${identity.bootstrap.subject:}") String subject,
      @Value("${identity.bootstrap.permissions:catalog:read,catalog:write,order:read:self,order:read:any,order:manage,rbac:manage}") Set<String> permissions) {
    return ignored -> { if (enabled) bootstrap.bootstrap(issuer, subject, permissions); };
  }
}
