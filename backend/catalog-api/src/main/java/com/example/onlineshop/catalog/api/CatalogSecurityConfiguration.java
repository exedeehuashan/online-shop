package com.example.onlineshop.catalog.api;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
class CatalogSecurityConfiguration {
  @Bean
  @ConditionalOnProperty(name="catalog.security.enabled", havingValue="true")
  JwtDecoder catalogJwtDecoder(@Value("${catalog.security.jwk-set-uri}") String jwks,
      @Value("${catalog.security.issuer}") String issuer, @Value("${catalog.security.audience}") String audience,
      @Value("${catalog.security.algorithm:RS256}") String algorithm) {
    if (jwks.isBlank() || issuer.isBlank() || audience.isBlank()) throw new IllegalStateException("strict OIDC settings are required");
    if (!Set.of("RS256", "RS384", "RS512").contains(algorithm)) throw new IllegalStateException("unsupported OIDC algorithm");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.valueOf(algorithm)).build();
    OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "required audience is missing", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
    return decoder;
  }

  @Bean
  @ConditionalOnProperty(name="catalog.security.enabled", havingValue="true")
  SecurityFilterChain protectedCatalog(HttpSecurity http,
      @Value("${catalog.security.identity-base-url}") String identityBaseUrl) throws Exception {
    if (identityBaseUrl.isBlank()) throw new IllegalStateException("identity service URL is required when catalog security is enabled");
    RestClient identity = RestClient.builder().baseUrl(identityBaseUrl).build();
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/products").hasAuthority("PERM_catalog:write")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/**").permitAll()
            .anyRequest().denyAll())
        .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
          Collection<org.springframework.security.core.GrantedAuthority> authorities;
          try {
            MeResponse me = identity.get().uri("/api/v1/me")
                .header("Authorization", "Bearer " + token.getTokenValue()).retrieve().body(MeResponse.class);
            authorities = me == null || me.permissions() == null ? List.of()
                : me.permissions().stream().map(permission ->
                    (org.springframework.security.core.GrantedAuthority) () -> "PERM_" + permission).toList();
          } catch (RuntimeException denied) {
            authorities = List.of();
          }
          return new JwtAuthenticationToken(token, authorities);
        }))).build();
  }

  @Bean
  @ConditionalOnProperty(name="catalog.security.enabled", havingValue="false", matchIfMissing=true)
  SecurityFilterChain legacyCatalog(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
  }

  private record MeResponse(UUID id, String displayName, Set<String> permissions) {}
}
