package com.example.onlineshop.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
final class IdentityExceptionHandler {
  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<ProblemDetail> malformed(Exception error, HttpServletRequest request) {
    return problem(400, "MALFORMED_REQUEST", "Malformed request", "The request could not be parsed", request);
  }

  @ExceptionHandler(AdminValidationException.class)
  ResponseEntity<ProblemDetail> invalid(AdminValidationException error, HttpServletRequest request) {
    return problem(422, "VALIDATION_FAILED", "Validation failed", error.getMessage(), request);
  }

  @ExceptionHandler(EmptyResultDataAccessException.class)
  ResponseEntity<ProblemDetail> missing(EmptyResultDataAccessException error, HttpServletRequest request) {
    return problem(404, "RESOURCE_NOT_FOUND", "Resource not found", "The requested identity resource does not exist", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException error, HttpServletRequest request) {
    return problem(409, "CONFLICT", "Conflict", "The requested change conflicts with the current identity state", request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
    return problem(500, "INTERNAL_ERROR", "Internal server error", "The request could not be completed", request);
  }

  private ResponseEntity<ProblemDetail> problem(int status, String code, String title, String detail, HttpServletRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
    body.setTitle(title);
    body.setType(URI.create("https://online-shop.example/problems/" + code.toLowerCase().replace('_', '-')));
    body.setInstance(URI.create(request.getRequestURI()));
    body.setProperty("code", code);
    body.setProperty("violations", List.of());
    body.setProperty("correlationId", request.getHeader(CorrelationIdFilter.HEADER));
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }
}
