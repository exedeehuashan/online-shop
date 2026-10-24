package com.example.onlineshop.identity.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class CorrelationIdFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Correlation-ID";
  static final String ATTRIBUTE = CorrelationIdFilter.class.getName();

  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader(HEADER);
    String value = valid(supplied) ? supplied : UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
    response.setHeader(HEADER, value);
    chain.doFilter(request, response);
  }

  private static boolean valid(String value) {
    if (value == null || value.isEmpty() || value.length() > 128) return false;
    return value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
  }
}
