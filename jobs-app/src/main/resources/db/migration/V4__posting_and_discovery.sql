-- Shared catalog: source listings, canonical postings, and synchronization history.
-- A job_listing is one source's record of a vacancy; a job_posting is the canonical vacancy several listings may
-- merge into. Postings and listings are never deleted. A failed sync never closes a listing: availability is
-- OPEN / CLOSED / UNKNOWN and only authoritative evidence may set CLOSED.

create table job_posting
(
    id                uuid primary key,
    employer_id       uuid         not null references employer (id),
    title             varchar(500) not null,
    title_normalized  varchar(500) not null,
    seniority         varchar(16)  not null default 'UNKNOWN',
    employment_type   varchar(16)  not null default 'UNKNOWN',
    workplace_type    varchar(16)  not null default 'UNKNOWN',
    salary_min        numeric(14, 2),
    salary_max        numeric(14, 2),
    salary_currency   char(3),
    salary_period     varchar(8),
    description_text  text,
    dedup_fingerprint varchar(64)  not null,
    availability      varchar(8)   not null default 'UNKNOWN',
    first_seen_at     timestamptz  not null,
    last_seen_at      timestamptz  not null,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null,
    constraint ck_job_posting_seniority check (seniority in
        ('INTERN', 'ENTRY', 'MID', 'SENIOR', 'STAFF', 'PRINCIPAL', 'MANAGER', 'UNKNOWN')),
    constraint ck_job_posting_employment_type check (employment_type in
        ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'TEMPORARY', 'INTERNSHIP', 'UNKNOWN')),
    constraint ck_job_posting_workplace_type check (workplace_type in ('REMOTE', 'HYBRID', 'ONSITE', 'UNKNOWN')),
    constraint ck_job_posting_availability check (availability in ('OPEN', 'CLOSED', 'UNKNOWN')),
    constraint ck_job_posting_salary_period check (salary_period in ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR')),
    constraint ck_job_posting_salary_range check (salary_min is null or salary_max is null or salary_min <= salary_max),
    constraint ck_job_posting_salary_currency check ((salary_min is null and salary_max is null) or salary_currency is not null),
    constraint ck_job_posting_seen check (first_seen_at <= last_seen_at)
);
create index ix_job_posting_employer_fingerprint on job_posting (employer_id, dedup_fingerprint);
create index ix_job_posting_availability_seen on job_posting (availability, last_seen_at desc);

create table job_posting_location
(
    id             uuid primary key,
    job_posting_id uuid         not null references job_posting (id) on delete cascade,
    raw_text       varchar(500) not null,
    city           varchar(200),
    region         varchar(200),
    country_code   char(2),
    is_remote      boolean      not null default false,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    version        bigint       not null,
    constraint ck_job_posting_location_country check (country_code ~ '^[A-Z]{2}$')
);
create index ix_job_posting_location_posting on job_posting_location (job_posting_id);
create index ix_job_posting_location_country on job_posting_location (country_code);

create table job_listing
(
    id                    uuid primary key,
    employer_source_id    uuid         not null references employer_source (id),
    job_posting_id        uuid         not null references job_posting (id),
    external_id           varchar(200) not null,
    title_raw             varchar(500) not null,
    location_raw          jsonb        not null default '[]',
    department            varchar(300),
    employment_type_raw   varchar(100),
    listing_url           varchar(2048),
    application_url       varchar(2048),
    published_at          timestamptz,
    source_updated_at     timestamptz,
    first_seen_at         timestamptz  not null,
    last_seen_at          timestamptz  not null,
    last_checked_at       timestamptz  not null,
    availability          varchar(8)   not null default 'UNKNOWN',
    availability_evidence text,
    content_hash          varchar(64)  not null,
    source_metadata       jsonb        not null default '{}',
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null,
    version               bigint       not null,
    -- Idempotent upsert key for discovery.
    constraint uk_job_listing_external unique (employer_source_id, external_id),
    -- (id, job_posting_id) lets applications reference a listing that provably belongs to their posting.
    constraint uk_job_listing_id_posting unique (id, job_posting_id),
    constraint ck_job_listing_availability check (availability in ('OPEN', 'CLOSED', 'UNKNOWN')),
    constraint ck_job_listing_location_array check (jsonb_typeof(location_raw) = 'array'),
    constraint ck_job_listing_metadata_object check (jsonb_typeof(source_metadata) = 'object'),
    constraint ck_job_listing_seen check (first_seen_at <= last_seen_at)
);
create index ix_job_listing_posting on job_listing (job_posting_id);
create index ix_job_listing_checked on job_listing (availability, last_checked_at);

create table sync_run
(
    id                 uuid primary key,
    job_source_id      uuid        not null references job_source (id),
    employer_source_id uuid references employer_source (id),
    trigger            varchar(16) not null,
    dry_run            boolean     not null,
    status             varchar(16) not null,
    started_at         timestamptz not null,
    finished_at        timestamptz,
    discovered_count   integer     not null default 0,
    inserted_count     integer     not null default 0,
    updated_count      integer     not null default 0,
    unchanged_count    integer     not null default 0,
    closed_count       integer     not null default 0,
    failed_count       integer     not null default 0,
    error_summary      text,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    version            bigint      not null,
    constraint ck_sync_run_trigger check (trigger in ('MANUAL', 'SCHEDULED')),
    constraint ck_sync_run_status check (status in ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'ABORTED')),
    constraint ck_sync_run_finished check ((status = 'RUNNING') = (finished_at is null)),
    constraint ck_sync_run_counts check (discovered_count >= 0 and inserted_count >= 0 and updated_count >= 0
        and unchanged_count >= 0 and closed_count >= 0 and failed_count >= 0)
);
-- Single-flight at the database level: at most one running sync per employer binding, and per whole source.
create unique index uk_sync_run_running_binding on sync_run (employer_source_id)
    where status = 'RUNNING' and employer_source_id is not null;
create unique index uk_sync_run_running_source on sync_run (job_source_id)
    where status = 'RUNNING' and employer_source_id is null;
create index ix_sync_run_source_started on sync_run (job_source_id, started_at desc);

create table sync_error
(
    id           uuid primary key,
    sync_run_id  uuid        not null references sync_run (id) on delete cascade,
    kind         varchar(16) not null,
    external_ref varchar(200),
    message      text        not null,
    occurred_at  timestamptz not null,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    version      bigint      not null,
    constraint ck_sync_error_kind check (kind in ('TRANSIENT', 'RATE_LIMITED', 'AUTH', 'NOT_FOUND', 'PARSE', 'PERMANENT'))
);
create index ix_sync_error_run on sync_error (sync_run_id);
