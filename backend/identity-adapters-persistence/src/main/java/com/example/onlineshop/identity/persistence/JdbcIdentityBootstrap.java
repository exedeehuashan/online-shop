package com.example.onlineshop.identity.persistence;

import com.example.onlineshop.identity.application.IdentityBootstrap;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcIdentityBootstrap implements IdentityBootstrap {
  public static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final UUID CUSTOMER_ROLE = UUID.fromString("20000000-0000-0000-0000-000000000001");
  public static final UUID CATALOG_MANAGER_ROLE = UUID.fromString("20000000-0000-0000-0000-000000000002");
  public static final UUID ORDER_MANAGER_ROLE = UUID.fromString("20000000-0000-0000-0000-000000000003");
  public static final UUID ACCESS_ADMIN_ROLE = UUID.fromString("20000000-0000-0000-0000-000000000004");
  private static final List<RoleDefinition> ROLES = List.of(
      new RoleDefinition(CUSTOMER_ROLE, "customer", "Customer", Set.of("catalog:read", "order:read:self")),
      new RoleDefinition(CATALOG_MANAGER_ROLE, "catalog_manager", "Catalog manager", Set.of("catalog:read", "catalog:write")),
      new RoleDefinition(ORDER_MANAGER_ROLE, "order_manager", "Order manager", Set.of("order:read:any", "order:manage")),
      new RoleDefinition(ACCESS_ADMIN_ROLE, "access_admin", "Access administrator", Set.of("rbac:manage")));
  private static final Set<String> REQUIRED_PERMISSIONS = ROLES.stream()
      .flatMap(role -> role.permissions().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
  private static final long BOOTSTRAP_LOCK = 0x4944454e54495459L;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  public JdbcIdentityBootstrap(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc = jdbc; this.tx = tx; }

  @Override public void bootstrap(String issuer, String subject, Set<String> codes) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("both first-admin issuer and subject are required");
    }
    if (codes == null || codes.isEmpty()) throw new IllegalArgumentException("permission codes are required");
    Set<String> requestedCodes = Set.copyOf(codes);
    Set<String> unsupported = new TreeSet<>(requestedCodes); unsupported.removeAll(REQUIRED_PERMISSIONS);
    if (!unsupported.isEmpty()) throw new IllegalArgumentException("unknown permission codes: " + unsupported);
    Set<String> missing = new TreeSet<>(REQUIRED_PERMISSIONS); missing.removeAll(requestedCodes);
    if (!missing.isEmpty()) throw new IllegalArgumentException("missing permission codes: " + missing);
    tx.executeWithoutResult(ignored -> doBootstrap(issuer, subject, requestedCodes));
  }

  private void doBootstrap(String issuer, String subject, Set<String> codes) {
    // One transaction owns bootstrap reconciliation across all application instances. This also
    // makes first-administrator creation deterministic for both equal and different subjects.
    jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, BOOTSTRAP_LOCK);
    List<Map<String,Object>> permissions = jdbc.queryForList(
        "select id,code from sys_permission where status='ACTIVE' and code in ("
            + String.join(",", Collections.nCopies(codes.size(), "?")) + ")",
        codes.toArray());
    Set<String> found = new HashSet<>();
    permissions.forEach(row -> found.add((String) row.get("code")));
    if (!found.equals(codes)) {
      Set<String> unknown = new TreeSet<>(codes); unknown.removeAll(found);
      throw new IllegalArgumentException("unknown permission codes: " + unknown);
    }
    UUID proposedUserId = UUID.nameUUIDFromBytes((issuer + "\n" + subject).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    int userCreated = jdbc.update("""
        insert into sys_user(id,issuer,subject,display_name,status,created_at,created_by,updated_at,updated_by)
        values (?,?,?,'Bootstrap administrator','ACTIVE',current_timestamp,?,current_timestamp,?)
        on conflict do nothing
        """, proposedUserId, issuer, subject, SYSTEM_ACTOR, SYSTEM_ACTOR);
    List<Map<String, Object>> users = jdbc.queryForList(
        "select id,status from sys_user where issuer=? and subject=? for update", issuer, subject);
    if (users.size() != 1) {
      throw new IllegalStateException("bootstrap principal identity conflicts with an existing identifier");
    }
    UUID userId = (UUID) users.get(0).get("id");
    if (!"ACTIVE".equals(users.get(0).get("status"))) {
      throw new IllegalStateException("bootstrap principal must be ACTIVE");
    }
    Map<String, UUID> permissionIds = new HashMap<>();
    permissions.forEach(row -> permissionIds.put((String) row.get("code"), (UUID) row.get("id")));
    int reconciled = 0;
    for (RoleDefinition role : ROLES) {
      reconciled += jdbc.update("""
          insert into sys_role(id,code,name,description,system_role,status,created_at,created_by,updated_at,updated_by)
          values (?,?,?,'Bootstrap system role',true,'ACTIVE',current_timestamp,?,current_timestamp,?)
          on conflict do nothing
          """, role.id(), role.code(), role.name(), SYSTEM_ACTOR, SYSTEM_ACTOR);
      List<Map<String, Object>> persistedRoles = jdbc.queryForList(
          "select id,system_role,status from sys_role where code=? for update", role.code());
      if (persistedRoles.size() != 1) {
        throw new IllegalStateException("system role definition mismatch for code " + role.code());
      }
      Map<String, Object> persistedRole = persistedRoles.get(0);
      if (!role.id().equals(persistedRole.get("id")) || !Boolean.TRUE.equals(persistedRole.get("system_role"))
          || !"ACTIVE".equals(persistedRole.get("status"))) {
        throw new IllegalStateException("system role definition mismatch for code " + role.code());
      }
      for (String code : role.permissions()) reconciled += jdbc.update("""
          insert into sys_role_permission(role_id,permission_id,granted_at,granted_by) values (?,?,current_timestamp,?)
          on conflict (role_id,permission_id) do nothing
          """, role.id(), permissionIds.get(code), SYSTEM_ACTOR);
    }
    reconciled += jdbc.update("""
        insert into sys_user_role(user_id,role_id,granted_at,granted_by) values (?,?,current_timestamp,?)
        on conflict (user_id,role_id) do nothing
        """, userId, ACCESS_ADMIN_ROLE, SYSTEM_ACTOR);
    String reason = userCreated == 1 ? "BOOTSTRAP_CREATED"
        : reconciled > 0 ? "BOOTSTRAP_RECONCILED" : "IDEMPOTENT_BOOTSTRAP";
    audit("BOOTSTRAP_ADMIN", userId, reason);
  }

  private void audit(String action, UUID target, String reason) {
    jdbc.update("""
        insert into security_audit_event(id,actor_id,actor_label,target_type,target_id,action,occurred_at,correlation_id,outcome,reason_code)
        values (?,?, 'identity-bootstrap','sys_user',?,?,current_timestamp,?,'SUCCEEDED',?)
        """, UUID.randomUUID(), SYSTEM_ACTOR, target, action, UUID.randomUUID(), reason);
  }

  private record RoleDefinition(UUID id, String code, String name, Set<String> permissions) {}
}
