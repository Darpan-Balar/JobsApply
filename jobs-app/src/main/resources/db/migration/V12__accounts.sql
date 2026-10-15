-- Phase 8: local accounts (ADR-0013). Passwords are stored only as BCrypt hashes. A user without a password cannot
-- sign in; the first account is set up from this machine and becomes ADMIN.
alter table app_user
    add column password_hash       varchar(100),
    add column password_changed_at timestamptz,
    add column role                varchar(16) not null default 'USER';

alter table app_user
    add constraint ck_app_user_role check (role in ('USER', 'ADMIN')),
    add constraint ck_app_user_password_changed check ((password_hash is null) = (password_changed_at is null));
