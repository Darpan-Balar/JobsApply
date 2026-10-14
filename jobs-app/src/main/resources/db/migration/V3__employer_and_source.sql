-- Shared catalog: employers, configured job-source instances, and employer <-> source bindings.
-- An employer is not a job source; one adapter serves many employers; one employer may use many sources.
-- Catalog rows are disabled/archived, never hard-deleted (postings and applications reference them).

create table employer
(
    id                 uuid primary key,
    key                varchar(63)  not null,
    display_name       varchar(200) not null,
    normalized_name    varchar(200) not null,
    website_url        varchar(2048),
    careers_url        varchar(2048),
    industry           varchar(100),
    tags               text[]       not null default '{}',
    headquarters       varchar(200),
    metadata           jsonb        not null default '{}',
    enabled            boolean      not null default true,
    discovery_priority smallint     not null default 50,
    archived_at        timestamptz,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null,
    version            bigint       not null,
    constraint uk_employer_key unique (key),
    constraint ck_employer_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
    constraint ck_employer_priority check (discovery_priority between 0 and 100),
    constraint ck_employer_metadata_object check (jsonb_typeof(metadata) = 'object')
);
create index ix_employer_normalized_name on employer (normalized_name);
create index ix_employer_enabled_priority on employer (enabled, discovery_priority desc);
create index ix_employer_tags on employer using gin (tags);

create table job_source
(
    id                      uuid primary key,
    key                     varchar(63)  not null,
    adapter_id              varchar(100) not null,
    display_name            varchar(200) not null,
    enabled                 boolean      not null default true,
    settings                jsonb        not null default '{}',
    settings_schema_version integer      not null default 1,
    created_at              timestamptz  not null,
    updated_at              timestamptz  not null,
    version                 bigint       not null,
    constraint uk_job_source_key unique (key),
    constraint ck_job_source_key check (key ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
    constraint ck_job_source_adapter_id check (adapter_id ~ '^[a-z0-9][a-z0-9.-]{1,99}$'),
    constraint ck_job_source_settings_object check (jsonb_typeof(settings) = 'object'),
    constraint ck_job_source_settings_version check (settings_schema_version > 0)
);

create table employer_source
(
    id                   uuid primary key,
    employer_id          uuid         not null references employer (id),
    job_source_id        uuid         not null references job_source (id),
    external_ref         varchar(200) not null,
    enabled              boolean      not null default true,
    priority             smallint     not null default 50,
    settings             jsonb        not null default '{}',
    last_success_at      timestamptz,
    last_failure_at      timestamptz,
    consecutive_failures integer      not null default 0,
    last_error           text,
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    version              bigint       not null,
    -- A board token / site on a given source belongs to exactly one employer.
    constraint uk_employer_source_ref unique (job_source_id, external_ref),
    constraint ck_employer_source_priority check (priority between 0 and 100),
    constraint ck_employer_source_failures check (consecutive_failures >= 0),
    constraint ck_employer_source_settings_object check (jsonb_typeof(settings) = 'object')
);
create index ix_employer_source_employer on employer_source (employer_id);
