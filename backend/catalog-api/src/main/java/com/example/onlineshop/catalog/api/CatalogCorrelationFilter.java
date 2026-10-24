package com.example.onlineshop.catalog.api;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.UUID;import org.slf4j.MDC;import org.springframework.core.Ordered;import org.springframework.core.annotation.Order;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
final class CatalogCorrelationFilter extends OncePerRequestFilter {
 static final String HEADER="X-Correlation-ID", ATTRIBUTE=CatalogCorrelationFilter.class.getName();
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String raw=req.getHeader(HEADER);String id=valid(raw)?raw:UUID.randomUUID().toString();req.setAttribute(ATTRIBUTE,id);res.setHeader(HEADER,id);try(MDC.MDCCloseable ignored=MDC.putCloseable("correlationId",id)){chain.doFilter(req,res);}}
 static boolean valid(String s){return s!=null&&!s.isEmpty()&&s.length()<=128&&s.chars().allMatch(c->c>=0x21&&c<=0x7e);}
}
