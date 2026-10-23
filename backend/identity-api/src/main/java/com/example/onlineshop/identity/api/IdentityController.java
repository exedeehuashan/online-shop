package com.example.onlineshop.identity.api;

import com.example.onlineshop.identity.persistence.JdbcRbacAssignments;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
final class IdentityController {
  private final IdentityDirectory directory;
  private final JdbcRbacAssignments assignments;

  IdentityController(IdentityDirectory directory, JdbcRbacAssignments assignments) {
    this.directory = directory;
    this.assignments = assignments;
  }

  @GetMapping("/me") Me me(@AuthenticationPrincipal IdentityPrincipal principal) {
    return new Me(principal.id(), principal.displayName(), new TreeSet<>(principal.permissions()));
  }

  @GetMapping("/admin/users") List<IdentityDirectory.UserView> users(
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return directory.users(page, size); }
  @GetMapping("/admin/roles") List<IdentityDirectory.RoleView> roles(
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return directory.roles(page, size); }
  @GetMapping("/admin/permissions") List<IdentityDirectory.PermissionView> permissions(
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return directory.permissions(page, size); }

  @PutMapping("/admin/users/{userId}/roles/{roleId}")
  ResponseEntity<Void> grantRole(@PathVariable UUID userId, @PathVariable UUID roleId,
      @RequestBody(required = false) RoleGrant request, @AuthenticationPrincipal IdentityPrincipal actor,
      HttpServletRequest servletRequest) {
    Instant expiry = request == null ? null : request.expiresAt();
    if (expiry != null && !expiry.isAfter(Instant.now())) throw new AdminValidationException("expiresAt must be in the future");
    assignments.grantRole(userId, roleId, actor.id(), expiry, correlation(servletRequest));
    directory.invalidate(userId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/admin/users/{userId}/roles/{roleId}")
  ResponseEntity<Void> revokeRole(@PathVariable UUID userId, @PathVariable UUID roleId,
      @AuthenticationPrincipal IdentityPrincipal actor, HttpServletRequest servletRequest) {
    assignments.revokeRole(userId, roleId, actor.id(), correlation(servletRequest));
    directory.invalidate(userId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/admin/roles/{roleId}/permissions/{permissionId}")
  ResponseEntity<Void> grantPermission(@PathVariable UUID roleId, @PathVariable UUID permissionId,
      @AuthenticationPrincipal IdentityPrincipal actor, HttpServletRequest servletRequest) {
    assignments.grantPermission(roleId, permissionId, actor.id(), correlation(servletRequest));
    directory.invalidateAll();
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/admin/roles/{roleId}/permissions/{permissionId}")
  ResponseEntity<Void> revokePermission(@PathVariable UUID roleId, @PathVariable UUID permissionId,
      @AuthenticationPrincipal IdentityPrincipal actor, HttpServletRequest servletRequest) {
    assignments.revokePermission(roleId, permissionId, actor.id(), correlation(servletRequest));
    directory.invalidateAll();
    return ResponseEntity.noContent().build();
  }

  private static UUID correlation(HttpServletRequest request) {
    return (UUID) Objects.requireNonNull(request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
  }

  record Me(UUID id, String displayName, Set<String> permissions) {}
  record RoleGrant(Instant expiresAt) {}
}
