package com.example.onlineshop.identity.persistence;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class IdentityPersistenceTest {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
  static DataSource dataSource;
  JdbcTemplate jdbc;
  JdbcIdentityBootstrap bootstrap;
  JdbcRbacAssignments assignments;

  @BeforeAll static void migrate() {
    PGSimpleDataSource source = new PGSimpleDataSource();
    source.setURL(POSTGRES.getJdbcUrl()); source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
    dataSource = source;
    Flyway.configure().dataSource(source).locations("classpath:db/identity").load().migrate();
  }

  @BeforeEach void clean() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update("truncate security_audit_event,sys_role_permission,sys_user_role,sys_role,sys_user cascade");
    TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    bootstrap = new JdbcIdentityBootstrap(jdbc, tx);
    assignments = new JdbcRbacAssignments(jdbc, tx);
  }

  @Test void schemaHasFiveOwnedTablesConstraintsIndexesAndNoCredentialColumns() {
    assertThat(jdbc.queryForList("""
        select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE'
        """, String.class)).contains("sys_user", "sys_role", "sys_permission", "sys_user_role", "sys_role_permission");
    assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname='public'", String.class))
        .contains("idx_sys_user_status_id", "idx_sys_user_role_role_user", "idx_sys_user_role_active_expiry",
            "idx_sys_role_permission_permission_role");
    assertThat(jdbc.queryForList("""
        select lower(column_name) from information_schema.columns where table_schema='public'
          and (column_name ~* '(password|token|secret|private.?key|security.?answer)')
        """, String.class)).isEmpty();
  }

  @Test void immutablePermissionSeedAndSystemRoleRulesAreDatabaseEnforced() {
    assertThat(jdbc.queryForList("select code from sys_permission order by code", String.class))
        .containsExactly("catalog:read", "catalog:write", "order:manage", "order:read:any", "order:read:self", "rbac:manage");
    assertThatThrownBy(() -> jdbc.update("update sys_permission set code='catalog:delete' where code='catalog:read'"))
        .hasMessageContaining("permission codes are immutable");
    assertThatThrownBy(() -> jdbc.update("delete from sys_permission where code='catalog:read'"))
        .hasMessageContaining("permission definitions cannot be deleted");
    bootstrap.bootstrap("https://id.example", "admin-1", allPermissions());
    assertThatThrownBy(() -> jdbc.update("update sys_role set code='renamed' where code='access_admin'"))
        .hasMessageContaining("system roles cannot be deleted or renamed");
    assertThatThrownBy(() -> jdbc.update("delete from sys_role where code='access_admin'"))
        .hasMessageContaining("system roles cannot be deleted or renamed");
    assertThatThrownBy(() -> jdbc.update("update sys_role set system_role=false where code='access_admin'"))
        .hasMessageContaining("system roles cannot be deleted or renamed");
  }

  @Test void bootstrapIsTransactionalIdempotentAuditedAndFailsClosedForUnknownCodes() {
    Set<String> permissions = Set.of(
        "catalog:read", "catalog:write", "order:read:self", "order:read:any", "order:manage", "rbac:manage");
    bootstrap.bootstrap("https://id.example", "admin-1", permissions);
    bootstrap.bootstrap("https://id.example", "admin-1", permissions);
    assertThat(jdbc.queryForObject("select count(*) from sys_user", Integer.class)).isOne();
    assertThat(jdbc.queryForObject("select count(*) from sys_role", Integer.class)).isEqualTo(4);
    assertThat(jdbc.queryForObject("select count(*) from sys_role_permission", Integer.class)).isEqualTo(7);
    assertThat(jdbc.queryForList("select r.code from sys_user_role ur join sys_role r on r.id=ur.role_id", String.class))
        .containsExactly("access_admin");
    assertThat(jdbc.queryForObject("select count(*) from security_audit_event where actor_label='identity-bootstrap'", Integer.class)).isEqualTo(2);
    assertThatThrownBy(() -> bootstrap.bootstrap("https://id.example", "other", Set.of("unknown:permission")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown permission codes");
    assertThat(jdbc.queryForObject("select count(*) from sys_user", Integer.class)).isOne();
  }

  @Test void bootstrapUsesExistingPrincipalIdentityAndRejectsIncompleteGrantDefinitions() {
    UUID existingId = UUID.randomUUID();
    jdbc.update("""
        insert into sys_user(id,issuer,subject,display_name,status,created_at,updated_at)
        values (?, 'https://id.example', 'admin-1', 'Existing administrator', 'ACTIVE', current_timestamp, current_timestamp)
        """, existingId);
    Set<String> permissions = Set.of(
        "catalog:read", "catalog:write", "order:read:self", "order:read:any", "order:manage", "rbac:manage");

    bootstrap.bootstrap("https://id.example", "admin-1", permissions);

    assertThat(jdbc.queryForObject("select user_id from sys_user_role", UUID.class)).isEqualTo(existingId);
    assertThatThrownBy(() -> bootstrap.bootstrap("https://id.example", "admin-1", Set.of("rbac:manage")))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing permission codes");
  }

  @Test void effectivePermissionViewExcludesDisabledArchivedAndExpiredGrants() {
    bootstrap.bootstrap("https://id.example", "admin-1", allPermissions());
    UUID user = jdbc.queryForObject("select id from sys_user", UUID.class);
    assertThat(assignments.effectivePermissions(user)).containsExactly("rbac:manage");
    jdbc.update("update sys_user set status='DISABLED' where id=?", user);
    assertThat(assignments.effectivePermissions(user)).isEmpty();
    jdbc.update("update sys_user set status='ACTIVE' where id=?", user);
    jdbc.update("update sys_role set status='ARCHIVED' where id=?", JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE);
    assertThat(assignments.effectivePermissions(user)).isEmpty();
    jdbc.update("update sys_role set status='ACTIVE' where id=?", JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE);
    jdbc.update("update sys_permission set status='ARCHIVED' where code='rbac:manage'");
    assertThat(assignments.effectivePermissions(user)).isEmpty();
    jdbc.update("update sys_permission set status='ACTIVE' where code='rbac:manage'");
    jdbc.update("update sys_user_role set granted_at=?,expires_at=? where user_id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(2)), java.sql.Timestamp.from(Instant.now().minusSeconds(1)), user);
    assertThat(assignments.effectivePermissions(user)).isEmpty();
  }

  @Test void authorizationChangesAppendImmutableAuditEvents() {
    bootstrap.bootstrap("https://id.example", "admin-1", allPermissions());
    UUID user = jdbc.queryForObject("select id from sys_user", UUID.class);
    assignments.revokeRole(user, JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE, user, UUID.randomUUID());
    assignments.grantRole(user, JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE, user, null, UUID.randomUUID());
    assertThat(jdbc.queryForList("select action from security_audit_event order by occurred_at", String.class))
        .contains("ROLE_REVOKED", "ROLE_GRANTED");
    assertThatThrownBy(() -> jdbc.update("delete from security_audit_event"))
        .hasMessageContaining("security audit events are immutable");
  }

  @Test void concurrentDuplicateRoleGrantsConvergeToOneAssignmentAndAuditBothOutcomes() throws Exception {
    bootstrap.bootstrap("https://id.example", "admin-1", allPermissions());
    UUID user = jdbc.queryForObject("select id from sys_user", UUID.class);
    assignments.revokeRole(user, JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE, user, UUID.randomUUID());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try {
      Callable<Void> grant = () -> {
        barrier.await(5, TimeUnit.SECONDS);
        JdbcTemplate threadJdbc = new JdbcTemplate(dataSource);
        new JdbcRbacAssignments(threadJdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)))
            .grantRole(user, JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE, user, null, UUID.randomUUID());
        return null;
      };
      Future<Void> first = pool.submit(grant); Future<Void> second = pool.submit(grant);
      first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
    } finally { pool.shutdownNow(); }
    assertThat(jdbc.queryForObject("select count(*) from sys_user_role where user_id=? and role_id=?", Integer.class,
        user, JdbcIdentityBootstrap.ACCESS_ADMIN_ROLE)).isOne();
    assertThat(jdbc.queryForObject("select count(*) from security_audit_event where action='ROLE_GRANTED'", Integer.class)).isEqualTo(2);
  }

  private static Set<String> allPermissions() {
    return Set.of("catalog:read", "catalog:write", "order:read:self", "order:read:any", "order:manage", "rbac:manage");
  }
}
