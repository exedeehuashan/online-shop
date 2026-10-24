package com.example.onlineshop.identity.api;

import com.example.onlineshop.identity.application.IdentityBootstrap;
import com.example.onlineshop.identity.persistence.JdbcIdentityBootstrap;
import com.example.onlineshop.identity.persistence.JdbcRbacAssignments;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Set;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile({"local", "uat"})
@ConditionalOnProperty(name = "identity.synthetic-seed.enabled", havingValue = "true")
class SyntheticIdentitySeedConfiguration {
  @Bean ApplicationRunner syntheticIdentitySeed(IdentityBootstrap bootstrap, JdbcRbacAssignments assignments,
      Environment environment) {
    return ignored -> {
      if (environment.matchesProfiles("prod", "production")) {
        throw new IllegalStateException("synthetic identity seed is forbidden in production");
      }
      String issuer = environment.getRequiredProperty("identity.synthetic-seed.issuer");
      String subject = environment.getProperty("identity.synthetic-seed.subject", "synthetic-admin");
      bootstrap.bootstrap(issuer, subject,
          Set.of("catalog:read", "catalog:write", "order:read:self", "order:read:any", "order:manage", "rbac:manage"));
      UUID userId = UUID.nameUUIDFromBytes((issuer + "\n" + subject).getBytes(StandardCharsets.UTF_8));
      assignments.grantRole(userId, JdbcIdentityBootstrap.CUSTOMER_ROLE, JdbcIdentityBootstrap.SYSTEM_ACTOR, null, UUID.randomUUID());
      assignments.grantRole(userId, JdbcIdentityBootstrap.ORDER_MANAGER_ROLE, JdbcIdentityBootstrap.SYSTEM_ACTOR, null, UUID.randomUUID());
    };
  }
}
