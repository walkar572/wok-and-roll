-- Refs #62. Users and roles for login (design: wok-and-roll-back/docs/auth.md section 7).
-- "user" is a reserved word in PostgreSQL, so the table is app_user (architecture.md section 4).
-- Passwords are stored only as BCrypt hashes, never as plain text.
-- Demo users for ADMIN, STAFF and VIEWER come later in a separate seed (#65).

CREATE TABLE role (
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    CONSTRAINT ck_role_code CHECK (code IN ('ADMIN', 'STAFF', 'VIEWER', 'CUSTOMER'))
);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,  -- saved in lower case
    password_hash VARCHAR(100) NOT NULL,         -- BCrypt hash
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(30),
    role_id       BIGINT       NOT NULL REFERENCES role (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_app_user_role ON app_user (role_id);

INSERT INTO role (code) VALUES ('ADMIN'), ('STAFF'), ('VIEWER'), ('CUSTOMER');
