package com.example.onlineshop.identity.api;

import com.example.onlineshop.identity.application.IdentityBootstrap;
import java.util.Set;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@Profile({"local", "uat"})
@ConditionalOnProperty(name = "identity.synthetic-seed.enabled", havingValue = "true")
class SyntheticIdentitySeedConfiguration {
  @Bean ApplicationRunner syntheticIdentitySeed(IdentityBootstrap bootstrap, Environment environment) {
    return ignored -> {
      if (environment.matchesProfiles("prod", "production")) {
        throw new IllegalStateException("synthetic identity seed is forbidden in production");
      }
      bootstrap.bootstrap("https://synthetic.invalid/local", "synthetic-admin",
          Set.of("catalog:read", "catalog:write", "order:read:self", "order:read:any", "order:manage", "rbac:manage"));
    };
  }
}
