-- Role profiles (system templates + user-owned), candidate profiles (user-owned, immutable versions), match results.
-- Composite (id, owner_user_id) uniques let child rows prove at the database level that they share their parent's owner.

create table role_profile
(
    id                uuid primary key,
    -- NULL owner = system template visible to every user; otherwise private to the owner (ADR-0002).
    owner_user_id     uuid references app_user (id) on delete cascade,
    job_family_id     uuid         not null references job_family (id),
    key               varchar(63)  not null,
    display_name      varchar(200) not null,
    active_version_id uuid,
    archived_at       timestamptz,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null,
    constraint uk_role_profile_owner_key unique nulls not distinct (owner_user_id, key),
    constraint ck_role_profile_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$')
);

create table role_profile_version
(
    id              uuid primary key,
    role_profile_id uuid        not null references role_profile (id) on delete cascade,
    version_no      integer     not null,
    schema_version  integer     not null,
    definition      jsonb       not null,
    content_hash    varchar(64) not null,
    created_at      timestamptz not null,
    updated_at      timestamptz not null,
    version         bigint      not null,
    constraint uk_role_profile_version_no unique (role_profile_id, version_no),
    -- Re-importing identical content is a no-op.
    constraint uk_role_profile_version_hash unique (role_profile_id, content_hash),
    constraint uk_role_profile_version_id_profile unique (id, role_profile_id),
    constraint ck_role_profile_version_no check (version_no > 0),
    constraint ck_role_profile_version_schema check (schema_version > 0),
    constraint ck_role_profile_version_definition check (jsonb_typeof(definition) = 'object')
);
create trigger trg_role_profile_version_immutable
    before update on role_profile_version
    for each row execute function forbid_mutation();

-- The active version must belong to this profile.
alter table role_profile
    add constraint fk_role_profile_active_version
        foreign key (active_version_id, id) references role_profile_version (id, role_profile_id)
            on delete set null (active_version_id)
            deferrable initially deferred;

create table candidate_profile
(
    id                 uuid primary key,
    owner_user_id      uuid         not null references app_user (id) on delete cascade,
    key                varchar(63)  not null,
    display_name       varchar(200) not null,
    current_version_id uuid,
    archived_at        timestamptz,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    version            bigint       not null,
    constraint uk_candidate_profile_owner_key unique (owner_user_id, key),
    constraint uk_candidate_profile_id_owner unique (id, owner_user_id),
    constraint ck_candidate_profile_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$')
);

create table candidate_profile_version
(
    id                   uuid primary key,
    owner_user_id        uuid        not null,
    candidate_profile_id uuid        not null,
    version_no           integer     not null,
    schema_version       integer     not null,
    snapshot             jsonb       not null,
    content_hash         varchar(64) not null,
    note                 varchar(1000),
    created_at           timestamptz not null,
    updated_at           timestamptz not null,
    version              bigint      not null,
    constraint fk_candidate_profile_version_profile foreign key (candidate_profile_id, owner_user_id)
        references candidate_profile (id, owner_user_id) on delete cascade,
    constraint uk_candidate_profile_version_no unique (candidate_profile_id, version_no),
    constraint uk_candidate_profile_version_owner unique (id, candidate_profile_id, owner_user_id),
    constraint uk_candidate_profile_version_id_owner unique (id, owner_user_id),
    constraint ck_candidate_profile_version_no check (version_no > 0),
    constraint ck_candidate_profile_version_schema check (schema_version > 0),
    constraint ck_candidate_profile_version_snapshot check (jsonb_typeof(snapshot) = 'object')
);
create trigger trg_candidate_profile_version_immutable
    before update on candidate_profile_version
    for each row execute function forbid_mutation();

alter table candidate_profile
    add constraint fk_candidate_profile_current_version
        foreign key (current_version_id, id, owner_user_id)
            references candidate_profile_version (id, candidate_profile_id, owner_user_id)
            on delete set null (current_version_id)
            deferrable initially deferred;

create table job_match
(
    id                           uuid primary key,
    owner_user_id                uuid          not null references app_user (id) on delete cascade,
    job_posting_id               uuid          not null references job_posting (id),
    candidate_profile_version_id uuid          not null,
    role_profile_version_id      uuid          not null references role_profile_version (id),
    strategy_key                 varchar(63)   not null,
    strategy_version             integer       not null,
    score                        numeric(5, 2),
    eligibility                  varchar(16)   not null,
    result                       jsonb         not null,
    evaluated_at                 timestamptz   not null,
    created_at                   timestamptz   not null,
    updated_at                   timestamptz   not null,
    version                      bigint        not null,
    constraint fk_job_match_candidate_version foreign key (candidate_profile_version_id, owner_user_id)
        references candidate_profile_version (id, owner_user_id) on delete cascade,
    -- Re-evaluating the same inputs with the same strategy version updates the existing row (idempotent).
    constraint uk_job_match_inputs unique (owner_user_id, job_posting_id, candidate_profile_version_id,
                                           role_profile_version_id, strategy_key, strategy_version),
    constraint uk_job_match_id_owner unique (id, owner_user_id),
    constraint ck_job_match_score check (score is null or score between 0 and 100),
    constraint ck_job_match_eligibility check (eligibility in ('ELIGIBLE', 'NOT_ELIGIBLE', 'UNKNOWN')),
    constraint ck_job_match_strategy_version check (strategy_version > 0),
    constraint ck_job_match_result_object check (jsonb_typeof(result) = 'object')
);
create index ix_job_match_owner_posting on job_match (owner_user_id, job_posting_id);
create index ix_job_match_owner_score on job_match (owner_user_id, score desc);
