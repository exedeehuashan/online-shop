package com.example.onlineshop.identity.persistence;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcRbacAssignments {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  public JdbcRbacAssignments(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

  public void grantRole(UUID userId, UUID roleId, UUID actorId, Instant expiresAt, UUID correlationId) {
    requireAuditContext(actorId, correlationId);
    tx.executeWithoutResult(ignored -> {
      lock("sys_user", userId);
      lock("sys_role", roleId);
      int previous = jdbc.query("select expires_at from sys_user_role where user_id=? and role_id=?",
          rs -> rs.next() ? (rs.getTimestamp(1) == null ? 0 : 1) : -1, userId, roleId);
      int changed = jdbc.update("""
          insert into sys_user_role(user_id,role_id,granted_at,granted_by,expires_at)
          values (?,?,current_timestamp,?,?) on conflict (user_id,role_id) do update
          set granted_at=excluded.granted_at, granted_by=excluded.granted_by, expires_at=excluded.expires_at
          where sys_user_role.expires_at is distinct from excluded.expires_at
          """, userId, roleId, actorId, expiresAt == null ? null : java.sql.Timestamp.from(expiresAt));
      audit(actorId, "sys_user_role", userId, userId, roleId, null, "ROLE_GRANTED", correlationId,
          changed == 0 ? "ALREADY_GRANTED" : previous == -1 ? "GRANTED" : "EXPIRY_UPDATED");
    });
  }

  public void revokeRole(UUID userId, UUID roleId, UUID actorId, UUID correlationId) {
    requireAuditContext(actorId, correlationId);
    tx.executeWithoutResult(ignored -> {
      lock("sys_user", userId);
      lock("sys_role", roleId);
      int changed = jdbc.update("delete from sys_user_role where user_id=? and role_id=?", userId, roleId);
      audit(actorId, "sys_user_role", userId, userId, roleId, null, "ROLE_REVOKED", correlationId,
          changed == 1 ? "REVOKED" : "NOT_ASSIGNED");
    });
  }

  public Set<String> effectivePermissions(UUID userId) {
    return new TreeSet<>(jdbc.queryForList(
        "select permission_code from effective_user_permission where user_id=?", String.class, userId));
  }

  public void grantPermission(UUID roleId, UUID permissionId, UUID actorId, UUID correlationId) {
    requireAuditContext(actorId, correlationId);
    tx.executeWithoutResult(ignored -> {
      lock("sys_role", roleId);
      lock("sys_permission", permissionId);
      int changed = jdbc.update("""
          insert into sys_role_permission(role_id,permission_id,granted_at,granted_by)
          values (?,?,current_timestamp,?) on conflict (role_id,permission_id) do nothing
          """, roleId, permissionId, actorId);
      audit(actorId, "sys_role_permission", roleId, null, roleId, permissionId, "PERMISSION_GRANTED", correlationId,
          changed == 1 ? "GRANTED" : "ALREADY_GRANTED");
    });
  }

  public void revokePermission(UUID roleId, UUID permissionId, UUID actorId, UUID correlationId) {
    requireAuditContext(actorId, correlationId);
    tx.executeWithoutResult(ignored -> {
      lock("sys_role", roleId);
      lock("sys_permission", permissionId);
      int changed = jdbc.update("delete from sys_role_permission where role_id=? and permission_id=?", roleId, permissionId);
      audit(actorId, "sys_role_permission", roleId, null, roleId, permissionId, "PERMISSION_REVOKED", correlationId,
          changed == 1 ? "REVOKED" : "NOT_ASSIGNED");
    });
  }

  private void audit(UUID actor, String targetType, UUID target, UUID principal, UUID role,
      UUID permission, String action, UUID correlation, String reason) {
    jdbc.update("""
        insert into security_audit_event(
          id,actor_id,actor_label,target_type,target_id,principal_id,role_id,permission_id,
          action,occurred_at,correlation_id,outcome,reason_code)
        values (?,?, 'application-user',?,?,?,?,?,?,current_timestamp,?,'SUCCEEDED',?)
        """, UUID.randomUUID(), actor, targetType, target, principal, role, permission,
        action, correlation, reason);
  }

  private void lock(String table, UUID id) {
    jdbc.queryForObject("select id from " + table + " where id=? for update", UUID.class, id);
  }

  private static void requireAuditContext(UUID actorId, UUID correlationId) {
    Objects.requireNonNull(actorId, "actorId is required");
    Objects.requireNonNull(correlationId, "correlationId is required");
  }
}
