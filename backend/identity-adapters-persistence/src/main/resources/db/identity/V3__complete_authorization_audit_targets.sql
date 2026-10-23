ALTER TABLE security_audit_event
  ADD COLUMN principal_id UUID NULL,
  ADD COLUMN role_id UUID NULL,
  ADD COLUMN permission_id UUID NULL;

ALTER TABLE security_audit_event
  ADD CONSTRAINT ck_security_audit_assignment_targets CHECK (
    (action IN ('ROLE_GRANTED', 'ROLE_REVOKED')
      AND principal_id IS NOT NULL AND role_id IS NOT NULL AND permission_id IS NULL)
    OR
    (action IN ('PERMISSION_GRANTED', 'PERMISSION_REVOKED')
      AND principal_id IS NULL AND role_id IS NOT NULL AND permission_id IS NOT NULL)
    OR
    (action NOT IN ('ROLE_GRANTED', 'ROLE_REVOKED', 'PERMISSION_GRANTED', 'PERMISSION_REVOKED')
      AND principal_id IS NULL AND role_id IS NULL AND permission_id IS NULL)
  );

CREATE INDEX idx_security_audit_event_principal_role
  ON security_audit_event(principal_id, role_id, occurred_at DESC)
  WHERE principal_id IS NOT NULL;

CREATE INDEX idx_security_audit_event_role_permission
  ON security_audit_event(role_id, permission_id, occurred_at DESC)
  WHERE permission_id IS NOT NULL;
