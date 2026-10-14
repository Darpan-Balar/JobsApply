-- Applications (user-owned) with an explicit state machine (ADR-0006), their transition history, and the audit log.

create table application
(
    id                           uuid primary key,
    owner_user_id                uuid        not null references app_user (id) on delete cascade,
    job_posting_id               uuid        not null references job_posting (id),
    -- Destination listing; chosen during preparation, so nullable until then.
    job_listing_id               uuid,
    candidate_profile_id         uuid        not null,
    candidate_profile_version_id uuid        not null,
    role_profile_version_id      uuid        not null references role_profile_version (id),
    job_match_id                 uuid,
    status                       varchar(32) not null,
    status_changed_at            timestamptz not null,
    created_at                   timestamptz not null,
    updated_at                   timestamptz not null,
    version                      bigint      not null,
    constraint fk_application_listing foreign key (job_listing_id, job_posting_id)
        references job_listing (id, job_posting_id),
    constraint fk_application_candidate_profile foreign key (candidate_profile_id, owner_user_id)
        references candidate_profile (id, owner_user_id) on delete cascade,
    constraint fk_application_candidate_version foreign key (candidate_profile_version_id, candidate_profile_id, owner_user_id)
        references candidate_profile_version (id, candidate_profile_id, owner_user_id) on delete cascade,
    constraint fk_application_match foreign key (job_match_id, owner_user_id)
        references job_match (id, owner_user_id),
    constraint ck_application_status check (status in
        ('DISCOVERED', 'MATCHED', 'NOT_ELIGIBLE', 'PREPARED', 'READY_FOR_REVIEW', 'APPROVED', 'SUBMITTING',
         'SUBMITTED', 'SUBMISSION_UNCONFIRMED', 'FAILED', 'EXPIRED', 'REJECTED_BY_USER', 'WITHDRAWN'))
);

-- Duplicate-submission guard: at most one approved / in-flight / submitted / unconfirmed application per user per
-- canonical vacancy, regardless of candidate profile or source listing.
create unique index uk_application_live_per_posting on application (owner_user_id, job_posting_id)
    where status in ('APPROVED', 'SUBMITTING', 'SUBMITTED', 'SUBMISSION_UNCONFIRMED');

-- At most one open application per user, vacancy and candidate profile. Closed ones (rejected, withdrawn, expired)
-- do not block a fresh attempt.
create unique index uk_application_open_per_profile on application (owner_user_id, job_posting_id, candidate_profile_id)
    where status not in ('REJECTED_BY_USER', 'WITHDRAWN', 'EXPIRED');

create index ix_application_owner_status on application (owner_user_id, status);
create index ix_application_posting on application (job_posting_id);

create table application_status_history
(
    id             uuid primary key,
    application_id uuid        not null references application (id) on delete cascade,
    from_status    varchar(32),
    to_status      varchar(32) not null,
    actor_type     varchar(16) not null,
    actor_user_id  uuid,
    reason         varchar(1000),
    occurred_at    timestamptz not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    version        bigint      not null,
    constraint ck_application_status_history_actor check (actor_type in ('USER', 'SYSTEM')),
    constraint ck_application_status_history_user check (actor_type <> 'USER' or actor_user_id is not null)
);
create index ix_application_status_history_app on application_status_history (application_id, occurred_at);
create trigger trg_application_status_history_immutable
    before update on application_status_history
    for each row execute function forbid_mutation();

-- Append-only audit log. No FK to app_user: audit outlives users (actor is pseudonymized on user deletion).
create table audit_event
(
    id             uuid primary key,
    occurred_at    timestamptz  not null,
    actor_type     varchar(16)  not null,
    actor_user_id  uuid,
    aggregate_type varchar(100) not null,
    aggregate_id   uuid,
    event_type     varchar(100) not null,
    correlation_id varchar(100),
    payload        jsonb        not null default '{}',
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    version        bigint       not null,
    constraint ck_audit_event_actor check (actor_type in ('USER', 'SYSTEM')),
    constraint ck_audit_event_payload_object check (jsonb_typeof(payload) = 'object')
);
create index ix_audit_event_aggregate on audit_event (aggregate_type, aggregate_id, occurred_at);
create index ix_audit_event_actor on audit_event (actor_user_id, occurred_at);
-- Append-only, with one exception: pseudonymizing the actor (actor_user_id -> NULL) when a user is deleted.
create function audit_event_guard() returns trigger
    language plpgsql as
$$
begin
    if tg_op = 'UPDATE'
        and new.actor_user_id is null
        and old.actor_user_id is not null
        and (to_jsonb(new) - 'actor_user_id' - 'updated_at' - 'version')
            = (to_jsonb(old) - 'actor_user_id' - 'updated_at' - 'version') then
        return new;
    end if;
    raise exception '% on table audit_event is not allowed: the audit log is append-only', tg_op
        using errcode = 'P0001';
end;
$$;

create trigger trg_audit_event_append_only
    before update or delete on audit_event
    for each row execute function audit_event_guard();
