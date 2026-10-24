package com.example.onlineshop.identity.api;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

class IdentityDirectory {
  private final JdbcTemplate jdbc;
  private final EffectivePermissionCache cache;
  IdentityDirectory(JdbcTemplate jdbc, Duration ttl) { this.jdbc = jdbc; this.cache = new EffectivePermissionCache(ttl, Clock.systemUTC()); }

  Optional<IdentityPrincipal> active(String issuer, String subject) {
    List<Map<String,Object>> rows = jdbc.queryForList(
        "select id,display_name from sys_user where issuer=? and subject=? and status='ACTIVE'", issuer, subject);
    if (rows.size() != 1) return Optional.empty();
    UUID id = (UUID) rows.get(0).get("id");
    Set<String> permissions = cache.get(id, () -> new TreeSet<>(jdbc.queryForList(
        "select permission_code from effective_user_permission where user_id=?", String.class, id)));
    return Optional.of(new IdentityPrincipal(id, (String) rows.get(0).get("display_name"), permissions));
  }
  void invalidate(UUID user) { cache.invalidate(user); }
  void invalidateAll() { cache.invalidateAll(); }

  List<UserView> users(int page, int size) {
    int[] bounds = bounds(page, size);
    return jdbc.query("select id,issuer,subject,display_name,status,version from sys_user order by id limit ? offset ?",
        (rs, row) -> new UserView(rs.getObject("id", UUID.class), rs.getString("issuer"),
            rs.getString("subject"), rs.getString("display_name"), rs.getString("status"), rs.getLong("version")), bounds[0], bounds[1]);
  }

  List<RoleView> roles(int page, int size) {
    int[] bounds = bounds(page, size);
    return jdbc.query("select id,code,name,status,system_role,version from sys_role order by code,id limit ? offset ?",
        (rs, row) -> new RoleView(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
            rs.getString("status"), rs.getBoolean("system_role"), rs.getLong("version")), bounds[0], bounds[1]);
  }

  List<PermissionView> permissions(int page, int size) {
    int[] bounds = bounds(page, size);
    return jdbc.query("select id,code,description,status,version from sys_permission order by code,id limit ? offset ?",
        (rs, row) -> new PermissionView(rs.getObject("id", UUID.class), rs.getString("code"),
            rs.getString("description"), rs.getString("status"), rs.getLong("version")), bounds[0], bounds[1]);
  }

  private static int[] bounds(int page, int size) {
    if (page < 0 || size < 1 || size > 100) throw new AdminValidationException("page must be >= 0 and size must be between 1 and 100");
    try { return new int[] { size, Math.multiplyExact(page, size) }; }
    catch (ArithmeticException overflow) { throw new AdminValidationException("page offset is too large"); }
  }

  record UserView(UUID id, String issuer, String subject, String displayName, String status, long version) {}
  record RoleView(UUID id, String code, String name, String status, boolean systemRole, long version) {}
  record PermissionView(UUID id, String code, String description, String status, long version) {}
}
