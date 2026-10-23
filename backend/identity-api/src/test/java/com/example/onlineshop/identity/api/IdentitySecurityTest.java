package com.example.onlineshop.identity.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.onlineshop.identity.persistence.JdbcRbacAssignments;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = IdentityController.class, properties = "identity.security.enabled=true")
@Import({OidcSecurityConfiguration.class, CorrelationIdFilter.class})
class IdentitySecurityTest {
  private static final UUID USER = UUID.fromString("20000000-0000-0000-0000-000000000001");
  @Autowired MockMvc mvc;
  @MockitoBean IdentityDirectory directory;
  @MockitoBean JdbcRbacAssignments assignments;
  @MockitoBean JwtDecoder decoder;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean PlatformTransactionManager transactions;

  @BeforeEach void activeUser() {
    when(decoder.decode(anyString())).thenThrow(new JwtValidationException("invalid token",
        List.of(new OAuth2Error("invalid_token"))));
    when(directory.active(eq("https://issuer.example"), eq("subject-1")))
        .thenReturn(Optional.of(new IdentityPrincipal(USER, "Safe display name", Set.of("catalog:read"))));
  }

  @Test void missingAndMalformedTokensAre401() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized()).andExpect(content().contentType("application/problem+json"));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test void validExternalIdentityWithoutActiveLocalPrincipalIs403() throws Exception {
    when(directory.active(anyString(), anyString())).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/me").with(externalJwt())).andExpect(status().isForbidden());
  }

  @Test void meReturnsOnlyLocalPrincipalAndSortedEffectivePermissions() throws Exception {
    mvc.perform(get("/api/v1/me").header("X-Correlation-ID", "trace-123").with(externalJwt()))
        .andExpect(status().isOk()).andExpect(header().string("X-Correlation-ID", "trace-123"))
        .andExpect(jsonPath("$.id").value(USER.toString()))
        .andExpect(jsonPath("$.displayName").value("Safe display name"))
        .andExpect(jsonPath("$.permissions[0]").value("catalog:read"))
        .andExpect(jsonPath("$.accessToken").doesNotExist());
  }

  @Test void adminRequiresExactPermissionWithoutWildcardOrInheritance() throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(externalJwt())).andExpect(status().isForbidden());
    when(directory.active(anyString(), anyString()))
        .thenReturn(Optional.of(new IdentityPrincipal(USER, "Admin", Set.of("rbac:*", "rbac:manager"))));
    mvc.perform(get("/api/v1/admin/users").with(externalJwt())).andExpect(status().isForbidden());
  }

  @Test void authorizedRoleRevocationInvalidatesTargetImmediatelyAndCarriesAuditContext() throws Exception {
    UUID target = UUID.randomUUID(), role = UUID.randomUUID();
    when(directory.active(anyString(), anyString()))
        .thenReturn(Optional.of(new IdentityPrincipal(USER, "Admin", Set.of("rbac:manage"))));
    mvc.perform(delete("/api/v1/admin/users/{user}/roles/{role}", target, role)
            .header("X-Correlation-ID", "audit-42").with(externalJwt()))
        .andExpect(status().isNoContent());
    verify(assignments).revokeRole(eq(target), eq(role), eq(USER), any(UUID.class));
    verify(directory).invalidate(target);
  }

  private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor externalJwt() {
    return jwt().jwt(builder -> builder.issuer("https://issuer.example").subject("subject-1"));
  }
}
