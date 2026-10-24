package com.example.onlineshop.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class OrderSecurityConfiguration {
  @Bean OrderAccessPolicy orderAccessPolicy() { return new OrderAccessPolicy(); }

  @Bean @ConditionalOnProperty(name="order.security.enabled", havingValue="true")
  JwtDecoder orderJwtDecoder(@Value("${order.security.jwk-set-uri}") String jwks,
      @Value("${order.security.issuer}") String issuer, @Value("${order.security.audience}") String audience,
      @Value("${order.security.algorithm:RS256}") String algorithm,
      @Value("${order.security.clock-skew:PT0S}") Duration skew) {
    if (jwks.isBlank() || issuer.isBlank() || audience.isBlank()) throw new IllegalStateException("strict OIDC settings are required");
    if (!Set.of("RS256","RS384","RS512").contains(algorithm)) throw new IllegalStateException("unsupported OIDC algorithm");
    NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(jwks).jwsAlgorithm(SignatureAlgorithm.valueOf(algorithm)).build();
    OAuth2TokenValidator<Jwt> expiry=jwt -> jwt.getExpiresAt()!=null ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","exp claim is required",null));
    OAuth2TokenValidator<Jwt> audienceValidator=jwt -> jwt.getAudience()!=null && jwt.getAudience().contains(audience)
        ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","required audience is missing",null));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(skew), expiry, new JwtIssuerValidator(issuer), audienceValidator));
    return decoder;
  }

  @Bean @ConditionalOnProperty(name="order.security.enabled", havingValue="true")
  SecurityFilterChain protectedOrders(HttpSecurity http, @Value("${order.security.identity-base-url}") String identityBaseUrl,
                                      ObjectMapper json) throws Exception {
    RestClient identity=RestClient.builder().baseUrl(identityBaseUrl).build();
    return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll().requestMatchers("/api/v1/**","/api/v2/**").authenticated().anyRequest().denyAll())
      .oauth2ResourceServer(o->o.jwt(j->{}).authenticationEntryPoint((req,res,e)->problem(req,res,401,"AUTHENTICATION_REQUIRED")))
      .exceptionHandling(e->e.accessDeniedHandler((req,res,x)->problem(req,res,403,"ACCESS_DENIED")))
      .addFilterAfter(new PrincipalFilter(identity), BearerTokenAuthenticationFilter.class).build();
  }

  @Bean @ConditionalOnProperty(name="order.security.enabled", havingValue="false", matchIfMissing=true)
  SecurityFilterChain legacyOrders(HttpSecurity http) throws Exception {
    return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll()).build();
  }

  private record MeResponse(UUID id, Set<String> permissions) {}
  private static final class PrincipalFilter extends OncePerRequestFilter {
    private final RestClient identity;
    PrincipalFilter(RestClient identity){this.identity=identity;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
      Authentication auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
      if(auth instanceof JwtAuthenticationToken token){
        try {
          MeResponse me=identity.get().uri("/api/v1/me").header("Authorization","Bearer "+token.getToken().getTokenValue()).retrieve().body(MeResponse.class);
          if(me==null||me.id()==null){problem(req,res,403,"LOCAL_PRINCIPAL_DENIED");return;}
          Set<String> permissions=me.permissions()==null?Set.of():me.permissions();
          OrderPrincipal principal=new OrderPrincipal(me.id(),permissions);
          List<GrantedAuthority> authorities=permissions.stream().map(p->(GrantedAuthority)new SimpleGrantedAuthority("PERM_"+p)).toList();
          org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,authorities));
        } catch(RuntimeException denied){problem(req,res,403,"LOCAL_PRINCIPAL_DENIED");return;}
      }
      chain.doFilter(req,res);
    }
  }
  static void problem(HttpServletRequest req,HttpServletResponse res,int status,String code)throws IOException{
    res.setStatus(status);res.setContentType("application/problem+json");if(status==401)res.setHeader("WWW-Authenticate","Bearer");
    String correlation=Optional.ofNullable(req.getHeader("X-Correlation-ID")).filter(v->!v.isBlank()).orElseGet(()->UUID.randomUUID().toString());
    res.setHeader("X-Correlation-ID",correlation);
    res.getWriter().printf("{\"type\":\"https://online-shop.example/problems/%s\",\"title\":\"Request denied\",\"status\":%d,\"detail\":\"Request denied\",\"instance\":\"%s\",\"code\":\"%s\",\"violations\":[],\"correlationId\":\"%s\"}",code.toLowerCase(Locale.ROOT),status,escape(req.getRequestURI()),code,escape(correlation));
  }
  private static String escape(String value){return Objects.toString(value,"").replace("\\","\\\\").replace("\"","\\\"");}
}
