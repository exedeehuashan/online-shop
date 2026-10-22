package com.example.onlineshop.order.api;

import com.example.onlineshop.order.application.*;
import com.example.onlineshop.order.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> malformed(Exception e,HttpServletRequest r){return problem(400,"MALFORMED_REQUEST","Malformed request","Request JSON could not be parsed",r);}
  @ExceptionHandler({DomainValidationException.class})
  ResponseEntity<ProblemDetail> validation(DomainValidationException e,HttpServletRequest r){return problem(422,e.code().name(),"Validation failed",e.getMessage(),r);}
  @ExceptionHandler({MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class})
  ResponseEntity<ProblemDetail> badRequest(Exception e,HttpServletRequest r){return problem(400,"MALFORMED_REQUEST","Malformed request",e.getMessage(),r);}
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
    p.setType(URI.create("https://example.com/problems/"+code.toLowerCase().replace('_','-'))); p.setInstance(URI.create(request.getRequestURI()));
    p.setProperty("code",code); p.setProperty("violations",java.util.List.of()); p.setProperty("correlationId",request.getHeader("X-Correlation-ID"));
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
  }
}
