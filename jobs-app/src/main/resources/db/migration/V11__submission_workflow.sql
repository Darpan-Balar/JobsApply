-- Phase 6: approvals, submission attempts, audit subject (ADR-0006).

-- An approval is for one exact package and can be used once. At most one active (unconsumed, unrevoked) per application.
create table submission_approval
(
    id             uuid primary key,
    owner_user_id  uuid        not null,
    application_id uuid        not null,
    package_id     uuid        not null,
    package_hash   varchar(64) not null,
    approved_by    uuid        not null,
    approved_at    timestamptz not null,
    consumed_at    timestamptz,
    revoked_at     timestamptz,
    revoke_reason  varchar(200),
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    version        bigint      not null,
    constraint fk_submission_approval_application foreign key (application_id, owner_user_id)
        references application (id, owner_user_id) on delete cascade,
    constraint fk_submission_approval_package foreign key (package_id, application_id)
        references application_package (id, application_id),
    constraint ck_submission_approval_single_end check (consumed_at is null or revoked_at is null),
    constraint ck_submission_approval_by_owner check (approved_by = owner_user_id),
    constraint uk_submission_approval_id_owner unique (id, owner_user_id)
);
create unique index uk_submission_approval_active on submission_approval (application_id)
    where consumed_at is null and revoked_at is null;

-- One attempt per approval; at most one attempt in progress per application; idempotency keys are unique per user.
create table application_attempt
(
    id              uuid primary key,
    owner_user_id   uuid         not null,
    application_id  uuid         not null,
    approval_id     uuid         not null,
    package_id      uuid         not null,
    idempotency_key varchar(100) not null,
    channel         varchar(24)  not null,
    outcome         varchar(16)  not null,
    started_at      timestamptz  not null,
    finished_at     timestamptz,
    evidence        jsonb,
    verified_by     uuid,
    verified_at     timestamptz,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    version         bigint       not null,
    constraint fk_application_attempt_application foreign key (application_id, owner_user_id)
        references application (id, owner_user_id) on delete cascade,
    constraint fk_application_attempt_approval foreign key (approval_id, owner_user_id)
        references submission_approval (id, owner_user_id) on delete cascade,
    constraint uk_application_attempt_approval unique (approval_id),
    constraint uk_application_attempt_idempotency unique (owner_user_id, idempotency_key),
    constraint ck_application_attempt_channel check (channel in ('MANUAL', 'BROWSER_ASSISTED')),
    constraint ck_application_attempt_outcome check (outcome in ('IN_PROGRESS', 'CONFIRMED', 'FAILED', 'UNCONFIRMED')),
    constraint ck_application_attempt_finished check ((outcome = 'IN_PROGRESS') = (finished_at is null)),
    constraint ck_application_attempt_verified check ((verified_by is null) = (verified_at is null))
);
create unique index uk_application_attempt_in_progress on application_attempt (application_id) where outcome = 'IN_PROGRESS';
create index ix_application_attempt_application on application_attempt (application_id, started_at desc);

-- Whose data an audit event concerns (no FK: the audit log outlives users).
alter table audit_event add column subject_user_id uuid;
create index ix_audit_event_subject on audit_event (subject_user_id, occurred_at desc);
