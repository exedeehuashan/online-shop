package com.example.onlineshop.catalog.api;

import java.util.*;
import java.time.Duration;
import java.io.IOException;
import jakarta.servlet.http.*;
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
      @Value("${catalog.security.algorithm:RS256}") String algorithm,
      @Value("${catalog.security.clock-skew:PT0S}") Duration clockSkew) {
    if (jwks.isBlank() || issuer.isBlank() || audience.isBlank()) throw new IllegalStateException("strict OIDC settings are required");
    if (!Set.of("RS256", "RS384", "RS512").contains(algorithm)) throw new IllegalStateException("unsupported OIDC algorithm");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.valueOf(algorithm)).build();
    if (clockSkew.isNegative() || clockSkew.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalStateException("OIDC clock skew must be between PT0S and PT30S");
    OAuth2TokenValidator<Jwt> expiryRequired = jwt -> jwt.getExpiresAt() != null ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "exp claim is required", null));
    OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "required audience is missing", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(clockSkew), expiryRequired,
        new JwtIssuerValidator(issuer), audienceValidator));
    return decoder;
  }

  JwtDecoder catalogJwtDecoder(String jwks, String issuer, String audience, String algorithm) {
    return catalogJwtDecoder(jwks, issuer, audience, algorithm, Duration.ZERO);
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
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v2/products/**").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.HEAD, "/api/v2/products/**").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/products").hasAuthority("PERM_catalog:write")
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/**").permitAll()
            .anyRequest().denyAll())
        .oauth2ResourceServer(server -> server.authenticationEntryPoint((req,res,e) -> problem(req,res,401,"AUTHENTICATION_REQUIRED"))
          .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
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
        }))).exceptionHandling(errors -> errors.accessDeniedHandler((req,res,e) -> problem(req,res,403,"ACCESS_DENIED"))).build();
  }

  @Bean
  @ConditionalOnProperty(name="catalog.security.enabled", havingValue="false", matchIfMissing=true)
  SecurityFilterChain legacyCatalog(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
  }

  private record MeResponse(UUID id, String displayName, Set<String> permissions) {}

  private static void problem(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
    response.setStatus(status); response.setContentType("application/problem+json");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    String correlation = Optional.ofNullable(request.getHeader("X-Correlation-ID")).orElseGet(() -> UUID.randomUUID().toString());
    response.setHeader("X-Correlation-ID", correlation);
    response.getWriter().printf("{\"type\":\"https://online-shop.example/problems/%s\",\"title\":\"Request denied\",\"status\":%d,\"detail\":\"Request denied\",\"instance\":\"%s\",\"code\":\"%s\",\"violations\":[],\"correlationId\":\"%s\"}", code.toLowerCase(Locale.ROOT), status, json(request.getRequestURI()), code, json(correlation));
  }
  private static String json(String value) { return Objects.toString(value, "").replace("\\", "\\\\").replace("\"", "\\\""); }
}
