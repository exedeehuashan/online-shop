package com.example.onlineshop.order.api;
import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException; import java.util.UUID;
import org.springframework.core.Ordered; import org.springframework.core.annotation.Order; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
class OrderCorrelationFilter extends OncePerRequestFilter {
  static final String HEADER="X-Correlation-ID";
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
    String value=req.getHeader(HEADER); if(value==null||value.isBlank()||value.length()>128)value=UUID.randomUUID().toString();
    req.setAttribute(HEADER,value);res.setHeader(HEADER,value);chain.doFilter(req,res);
  }
  static String current(HttpServletRequest req){return String.valueOf(req.getAttribute(HEADER));}
}
