-- FR-AUTH-01: регистрация и вход по email и паролю.
-- Пароль хранится только в виде хэша Argon2id.

CREATE TABLE app_user (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             varchar(254) NOT NULL,
    email_normalized  varchar(254) NOT NULL,
    password_hash     varchar(255) NOT NULL,
    role              varchar(16)  NOT NULL DEFAULT 'USER',
    enabled           boolean      NOT NULL DEFAULT true,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_user_role_check
        CHECK (role IN ('GUEST', 'USER', 'ANALYST', 'ADMIN'))
);

CREATE UNIQUE INDEX app_user_email_normalized_uq
    ON app_user (email_normalized);
