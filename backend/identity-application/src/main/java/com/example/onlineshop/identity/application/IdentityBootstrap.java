package com.example.onlineshop.identity.application;

import java.util.Set;

public interface IdentityBootstrap {
  void bootstrap(String issuer, String subject, Set<String> permissionCodes);
}
