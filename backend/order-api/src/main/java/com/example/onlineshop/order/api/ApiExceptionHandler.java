package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(Exception e,HttpServletRequest r){return problem(403,"ACCESS_DENIED","Request denied","Request denied",r);}
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> malformed(Exception e,HttpServletRequest r){return problem(400,"MALFORMED_REQUEST","Malformed request","Request JSON could not be parsed",r);}
  @ExceptionHandler({DomainValidationException.class})
  ResponseEntity<ProblemDetail> validation(DomainValidationException e,HttpServletRequest r){return problem(422,e.code().name(),"Validation failed",e.getMessage(),r);}
  @ExceptionHandler({MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class,MalformedHeaderException.class})
  ResponseEntity<ProblemDetail> badRequest(Exception e,HttpServletRequest r){return problem(400,"MALFORMED_REQUEST","Malformed request",e.getMessage(),r);}
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> methodNotAllowed(Exception e,HttpServletRequest r){return problem(405,"METHOD_NOT_ALLOWED","Method not allowed",e.getMessage(),r);}
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> unsupportedMediaType(Exception e,HttpServletRequest r){return problem(415,"UNSUPPORTED_MEDIA_TYPE","Unsupported media type",e.getMessage(),r);}
  @ExceptionHandler(OrderNotFoundException.class)
  ResponseEntity<ProblemDetail> missing(Exception e,HttpServletRequest r){return problem(404,"ORDER_NOT_FOUND","Order not found","Order was not found",r);}
  @ExceptionHandler(IdempotencyConflictException.class)
  ResponseEntity<ProblemDetail> idempotency(Exception e,HttpServletRequest r){return problem(409,"IDEMPOTENCY_CONFLICT","Idempotency conflict",e.getMessage(),r);}
  @ExceptionHandler({OptimisticOrderLockException.class})
  ResponseEntity<ProblemDetail> optimistic(Exception e,HttpServletRequest r){return problem(409,"OPTIMISTIC_LOCK_CONFLICT","Concurrent update conflict",e.getMessage(),r);}
  @ExceptionHandler(OrderStateConflictException.class)
  ResponseEntity<ProblemDetail> state(Exception e,HttpServletRequest r){return problem(409,"ORDER_STATE_CONFLICT","Order state conflict",e.getMessage(),r);}
  private ResponseEntity<ProblemDetail> problem(int status,String code,String title,String detail,HttpServletRequest request){
    ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),detail); p.setTitle(title);
    p.setType(URI.create("https://github.com/exedeehuashan/online-shop/blob/main/docs/problem-details.md#"+code.toLowerCase().replace('_','-'))); p.setInstance(URI.create(request.getRequestURI()));
    p.setProperty("code",code);
    p.setProperty("violations",status==422?java.util.List.of(Map.of("field","request","message",detail)):java.util.List.of());
    String correlation=String.valueOf(request.getAttribute(OrderCorrelationFilter.HEADER));
    p.setProperty("correlationId",correlation);
    return ResponseEntity.status(status).header(OrderCorrelationFilter.HEADER,correlation).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
  }
}
