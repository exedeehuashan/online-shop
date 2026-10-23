package com.example.onlineshop.identity.api;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

@Configuration
class OidcSecurityConfiguration {
  @Bean IdentityDirectory identityDirectory(JdbcTemplate jdbc,
      @Value("${identity.security.permission-cache-ttl:PT30S}") Duration ttl) { return new IdentityDirectory(jdbc, ttl); }

  @Bean
  @ConditionalOnProperty(name="identity.security.enabled", havingValue="true")
  JwtDecoder jwtDecoder(@Value("${identity.security.jwk-set-uri}") String jwks,
      @Value("${identity.security.issuer}") String issuer,
      @Value("${identity.security.audience}") String audience,
      @Value("${identity.security.algorithm:RS256}") String algorithm,
      @Value("${identity.security.clock-skew:PT0S}") Duration clockSkew) {
    if (jwks.isBlank() || issuer.isBlank() || audience.isBlank()) throw new IllegalStateException("strict OIDC settings are required");
    if (!Set.of("RS256", "RS384", "RS512").contains(algorithm)) throw new IllegalStateException("unsupported OIDC algorithm");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.valueOf(algorithm)).build();
    if (clockSkew.isNegative() || clockSkew.compareTo(Duration.ofSeconds(30)) > 0)
      throw new IllegalStateException("OIDC clock skew must be between PT0S and PT30S");
    JwtTimestampValidator timestamps = new JwtTimestampValidator(clockSkew);
    OAuth2TokenValidator<Jwt> expiryRequired = jwt -> jwt.getExpiresAt() != null
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "exp claim is required", null));
    OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "required audience is missing", null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, expiryRequired,
        new JwtIssuerValidator(issuer), audienceValidator));
    return decoder;
  }

  JwtDecoder jwtDecoder(String jwks, String issuer, String audience, String algorithm) {
    return jwtDecoder(jwks, issuer, audience, algorithm, Duration.ZERO);
  }

  @Bean
  @ConditionalOnProperty(name="identity.security.enabled", havingValue="true")
  SecurityFilterChain secured(HttpSecurity http, IdentityDirectory directory) throws Exception {
    http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
          .requestMatchers("/actuator/health").permitAll()
          .requestMatchers("/api/v1/admin/**").hasAuthority("PERM_rbac:manage")
          .requestMatchers("/api/v1/me").authenticated().anyRequest().denyAll())
      .oauth2ResourceServer(o -> o.jwt(jwt -> {}).authenticationEntryPoint((req,res,e) -> problem(req,res,401,"AUTHENTICATION_REQUIRED")))
      .exceptionHandling(e -> e.accessDeniedHandler((req,res,x) -> problem(req,res,403,"ACCESS_DENIED")))
      .addFilterAfter(new LocalPrincipalFilter(directory), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(name="identity.security.enabled", havingValue="false", matchIfMissing=true)
  SecurityFilterChain disabled(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().denyAll()).build();
  }

  static void problem(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
    response.setStatus(status); response.setContentType("application/problem+json");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    String correlation = response.getHeader(CorrelationIdFilter.HEADER);
    response.getWriter().printf("{\"type\":\"https://online-shop.example/problems/%s\",\"title\":\"Request denied\",\"status\":%d,\"detail\":\"Request denied\",\"instance\":\"%s\",\"code\":\"%s\",\"violations\":[],\"correlationId\":\"%s\"}", code.toLowerCase(Locale.ROOT), status, json(request.getRequestURI()), code, json(correlation));
  }

  private static String json(String value) { return Objects.toString(value, "").replace("\\", "\\\\").replace("\"", "\\\""); }

  private static final class LocalPrincipalFilter extends OncePerRequestFilter {
    private final IdentityDirectory directory;
    LocalPrincipalFilter(IdentityDirectory directory) { this.directory = directory; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
      Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      if (auth instanceof JwtAuthenticationToken token) {
        String issuer = token.getToken().getIssuer() == null ? "" : token.getToken().getIssuer().toString();
        String subject = token.getToken().getSubject();
        Optional<IdentityPrincipal> principal = directory.active(issuer, subject);
        if (principal.isEmpty()) { problem(req, res, HttpStatus.FORBIDDEN.value(), "LOCAL_PRINCIPAL_DENIED"); return; }
        List<GrantedAuthority> authorities = principal.get().permissions().stream().map(p -> new SimpleGrantedAuthority("PERM_"+p)).map(GrantedAuthority.class::cast).toList();
        UsernamePasswordAuthenticationToken local = new UsernamePasswordAuthenticationToken(principal.get(), null, authorities);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(local);
      }
      chain.doFilter(req,res);
    }
  }
}
