CREATE TABLE sys_user (
  id UUID PRIMARY KEY,
  issuer VARCHAR(255) NOT NULL CHECK (char_length(issuer) > 0),
  subject VARCHAR(255) NOT NULL CHECK (char_length(subject) > 0),
  email VARCHAR(320) NULL CHECK (email = lower(trim(email))),
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  display_name VARCHAR(200) NOT NULL CHECK (char_length(trim(display_name)) > 0),
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','DISABLED')),
  last_login_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  created_by UUID NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  updated_by UUID NULL,
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  CONSTRAINT uq_sys_user_issuer_subject UNIQUE (issuer, subject),
  CONSTRAINT ck_sys_user_audit_time CHECK (updated_at >= created_at)
);
CREATE INDEX idx_sys_user_status_id ON sys_user(status, id);

CREATE TABLE sys_role (
  id UUID PRIMARY KEY,
  code VARCHAR(100) NOT NULL CHECK (code = lower(trim(code)) AND code ~ '^[a-z][a-z0-9_-]*$'),
  name VARCHAR(160) NOT NULL CHECK (char_length(trim(name)) > 0),
  description TEXT NULL,
  system_role BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL,
  created_by UUID NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  updated_by UUID NULL,
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  CONSTRAINT uq_sys_role_code UNIQUE (code),
  CONSTRAINT ck_sys_role_audit_time CHECK (updated_at >= created_at)
);

CREATE TABLE sys_permission (
  id UUID PRIMARY KEY,
  code VARCHAR(160) NOT NULL CHECK (code = lower(trim(code)) AND code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)?$'),
  description TEXT NOT NULL CHECK (char_length(trim(description)) > 0),
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
  created_at TIMESTAMPTZ NOT NULL,
  created_by UUID NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  updated_by UUID NULL,
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  CONSTRAINT uq_sys_permission_code UNIQUE (code),
  CONSTRAINT ck_sys_permission_audit_time CHECK (updated_at >= created_at)
);

CREATE TABLE sys_user_role (
  user_id UUID NOT NULL,
  role_id UUID NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL,
  granted_by UUID NULL,
  expires_at TIMESTAMPTZ NULL,
  CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE RESTRICT,
  CONSTRAINT ck_sys_user_role_expiry CHECK (expires_at IS NULL OR expires_at > granted_at)
);
CREATE INDEX idx_sys_user_role_role_user ON sys_user_role(role_id, user_id);
CREATE INDEX idx_sys_user_role_active_expiry ON sys_user_role(user_id, expires_at);

CREATE TABLE sys_role_permission (
  role_id UUID NOT NULL,
  permission_id UUID NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL,
  granted_by UUID NULL,
  CONSTRAINT pk_sys_role_permission PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
  CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE RESTRICT
);
CREATE INDEX idx_sys_role_permission_permission_role ON sys_role_permission(permission_id, role_id);

CREATE TABLE security_audit_event (
  id UUID PRIMARY KEY,
  actor_id UUID NULL,
  actor_label VARCHAR(120) NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id UUID NULL,
  action VARCHAR(120) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  correlation_id UUID NOT NULL,
  outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCEEDED','FAILED')),
  reason_code VARCHAR(120) NULL
);
CREATE INDEX idx_security_audit_event_target ON security_audit_event(target_type, target_id, occurred_at DESC);

CREATE FUNCTION protect_permission_code() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'permission definitions cannot be deleted' USING ERRCODE = 'check_violation';
  END IF;
  IF NEW.code IS DISTINCT FROM OLD.code THEN
    RAISE EXCEPTION 'permission codes are immutable' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;
CREATE FUNCTION protect_system_role() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.system_role AND TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'system roles cannot be deleted or renamed' USING ERRCODE = 'check_violation';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  IF OLD.system_role AND (NEW.code IS DISTINCT FROM OLD.code OR NEW.name IS DISTINCT FROM OLD.name
      OR NEW.system_role IS DISTINCT FROM OLD.system_role) THEN
    RAISE EXCEPTION 'system roles cannot be deleted or renamed' USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_sys_permission_immutable_code BEFORE UPDATE OR DELETE ON sys_permission
FOR EACH ROW EXECUTE FUNCTION protect_permission_code();
CREATE TRIGGER trg_sys_role_protect_system BEFORE UPDATE OR DELETE ON sys_role
FOR EACH ROW EXECUTE FUNCTION protect_system_role();

CREATE FUNCTION reject_security_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'security audit events are immutable' USING ERRCODE = 'check_violation';
END;
$$;
CREATE TRIGGER trg_security_audit_event_immutable BEFORE UPDATE OR DELETE ON security_audit_event
FOR EACH ROW EXECUTE FUNCTION reject_security_audit_mutation();

CREATE VIEW effective_user_permission AS
SELECT ur.user_id, p.code AS permission_code
FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id AND u.status = 'ACTIVE'
JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ACTIVE'
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 'ACTIVE'
WHERE ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP;
